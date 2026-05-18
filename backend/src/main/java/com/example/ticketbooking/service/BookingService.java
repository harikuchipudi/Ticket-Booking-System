package com.example.ticketbooking.service;

import com.example.ticketbooking.model.Ticket;
import com.example.ticketbooking.model.TicketStatus;
import com.example.ticketbooking.repository.TicketRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final com.example.ticketbooking.repository.OutboxEventRepository outboxEventRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private final Counter bookingConfirmedCounter;
    private final Counter bookingRejectedCounter;

    public BookingService(TicketRepository ticketRepository,
                          SeatLockService seatLockService,
                          org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                          com.example.ticketbooking.repository.OutboxEventRepository outboxEventRepository,
                          MeterRegistry meterRegistry) {
        this.ticketRepository = ticketRepository;
        this.seatLockService  = seatLockService;
        this.redisTemplate    = redisTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper     = new com.fasterxml.jackson.databind.ObjectMapper();

        this.bookingConfirmedCounter = Counter.builder("booking.confirmed")
                .description("Successfully persisted bookings")
                .register(meterRegistry);
        this.bookingRejectedCounter  = Counter.builder("booking.rejected")
                .description("Rejected booking attempts (lock mismatch or duplicate)")
                .register(meterRegistry);
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
        String lockKey   = LOCK_PREFIX + matchName + ":" + seatId;
        String lockOwner = redisTemplate.opsForValue().get(lockKey);

        if (!userId.equals(lockOwner)) {
            bookingRejectedCounter.increment();
            log.warn("Booking rejected — user {} does not own lock for match {} seat {}. Lock held by: {}",
                    userId, matchName, seatId, lockOwner);
            throw new SeatNotLockedException(
                    "You must select (lock) the seat before booking. " +
                    "The lock may have expired or is held by another user.");
        }

        // ── Step 2: Guard against double-booking at the DB level ──────────────
        if (ticketRepository.findByMatchNameAndSeat(matchName, seatId).isPresent()) {
            log.warn("Booking rejected — seat {} is already booked in the DB for match {}", seatId, matchName);
            throw new SeatAlreadyBookedException("Seat " + seatId + " is already booked for this match.");
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
            bookingConfirmedCounter.increment();
            log.info("Ticket persisted — match={} seat={} user={} id={}", matchName, seatId, userId, saved.getId());
        } catch (DataIntegrityViolationException ex) {
            // Extremely rare: two requests passed step 2 simultaneously.
            // The DB UNIQUE constraint on `seat` + `match_name` catches this.
            log.error("DB unique constraint triggered for match {} seat {} — possible race condition", matchName, seatId, ex);
            throw new SeatAlreadyBookedException("Seat " + seatId + " was just booked by another user.");
        }

        // ── Step 4 & 5: Save OutboxEvent for background processing ────────────
        // This solves the dual-write problem. The background processor will guarantee
        // that the Redis lock is deleted and the SSE broadcast is sent.
        try {
            com.example.ticketbooking.model.SeatLockEvent event = 
                new com.example.ticketbooking.model.SeatLockEvent(matchName, seatId, userId, "booked");
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new com.example.ticketbooking.model.OutboxEvent(matchName + ":" + seatId, "SEAT_BOOKED", payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to serialize OutboxEvent payload", e);
            throw new RuntimeException("Failed to save outbox event", e);
        }

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
