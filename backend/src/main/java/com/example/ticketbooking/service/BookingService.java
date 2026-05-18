package com.example.ticketbooking.service;

import com.example.ticketbooking.model.Ticket;
import com.example.ticketbooking.model.TicketStatus;
import com.example.ticketbooking.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the atomic booking flow:
 *
 *   1. Verify the requesting user owns the Redis distributed lock for the seat.
 *   2. Guard against a seat that is already booked in the DB.
 *   3. Persist a new Ticket row in Neon PostgreSQL (UNIQUE constraint on seat
 *      is the final DB-level safety net against race conditions).
 *   4. Release the Redis lock.
 *   5. Broadcast the "booked" SSE event to all connected clients.
 *
 * The method is @Transactional so the DB write is atomic. If the DB commit
 * fails (e.g. duplicate seat constraint), the Redis lock is NOT released and
 * the SSE event is NOT published.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final String LOCK_PREFIX = "seat:lock:";

    private final TicketRepository    ticketRepository;
    private final SeatLockService     seatLockService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public BookingService(TicketRepository ticketRepository,
                          SeatLockService seatLockService,
                          org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.ticketRepository = ticketRepository;
        this.seatLockService  = seatLockService;
        this.redisTemplate    = redisTemplate;
    }

    /**
     * Books a seat atomically. Throws a descriptive runtime exception on failure
     * so the controller can map it to the correct HTTP status code.
     *
     * @param seatId      the seat being booked (e.g. "A1")
     * @param userId      the user claiming the booking
     * @param matchName   the event/match name
     * @param customerName display name for the ticket
     * @return the persisted Ticket entity
     */
    @Transactional
    public Ticket book(String seatId, String userId, String matchName, String customerName) {

        // ── Step 1: Verify the caller owns the Redis lock ─────────────────────
        String lockKey   = LOCK_PREFIX + seatId;
        String lockOwner = redisTemplate.opsForValue().get(lockKey);

        if (!userId.equals(lockOwner)) {
            log.warn("Booking rejected — user {} does not own lock for seat {}. Lock held by: {}",
                    userId, seatId, lockOwner);
            throw new SeatNotLockedException(
                    "You must select (lock) the seat before booking. " +
                    "The lock may have expired or is held by another user.");
        }

        // ── Step 2: Guard against double-booking at the DB level ──────────────
        if (ticketRepository.findBySeat(seatId).isPresent()) {
            log.warn("Booking rejected — seat {} is already booked in the DB", seatId);
            throw new SeatAlreadyBookedException("Seat " + seatId + " is already booked.");
        }

        // ── Step 3: Persist the booking to Neon PostgreSQL ────────────────────
        Ticket ticket = new Ticket(
                matchName    != null ? matchName    : "General Admission",
                seatId,
                customerName != null ? customerName : userId,
                userId
        );
        ticket.setStatus(TicketStatus.BOOKED);

        Ticket saved;
        try {
            saved = ticketRepository.save(ticket);
            log.info("Ticket persisted — seat={} user={} id={}", seatId, userId, saved.getId());
        } catch (DataIntegrityViolationException ex) {
            // Extremely rare: two requests passed step 2 simultaneously.
            // The DB UNIQUE constraint on `seat` catches this.
            log.error("DB unique constraint triggered for seat {} — possible race condition", seatId, ex);
            throw new SeatAlreadyBookedException("Seat " + seatId + " was just booked by another user.");
        }

        // ── Step 4 & 5: Release Redis lock and broadcast SSE event ────────────
        // These happen AFTER the DB commit succeeds (method is @Transactional).
        seatLockService.releaseAndBroadcastBooked(seatId, userId);

        return saved;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Domain Exceptions
    // ────────────────────────────────────────────────────────────────────────

    public static class SeatNotLockedException extends RuntimeException {
        public SeatNotLockedException(String msg) { super(msg); }
    }

    public static class SeatAlreadyBookedException extends RuntimeException {
        public SeatAlreadyBookedException(String msg) { super(msg); }
    }
}
