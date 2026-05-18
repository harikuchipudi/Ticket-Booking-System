package com.example.ticketbooking.service;

import com.example.ticketbooking.config.RedisConfig;
import com.example.ticketbooking.model.SeatLockEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages seat locks using Redis for centralized state and Pub/Sub for broadcasting.
 */
import com.example.ticketbooking.repository.TicketRepository;

@Service
public class SeatLockService {

    private static final Logger log = LoggerFactory.getLogger(SeatLockService.class);
    private static final long LOCK_TTL_SECONDS = 5 * 60; // 5 minutes
    private static final String LOCK_PREFIX = "seat:lock:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TicketRepository ticketRepository;

    // Emitters for clients connected specifically to this backend instance
    private final List<SseEmitter> localEmitters = new CopyOnWriteArrayList<>();

    public SeatLockService(StringRedisTemplate redisTemplate, TicketRepository ticketRepository) {
        this.redisTemplate    = redisTemplate;
        this.ticketRepository = ticketRepository;
        this.objectMapper     = new ObjectMapper();
    }


    // ──────────────────────────────────────────────
    //  SSE connection management
    // ──────────────────────────────────────────────

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        localEmitters.add(emitter);

        emitter.onCompletion(() -> localEmitters.remove(emitter));
        emitter.onTimeout(() -> localEmitters.remove(emitter));
        emitter.onError(e -> localEmitters.remove(emitter));

        // 1. Emit DB-persisted booked seats FIRST (permanent state, survives restarts)
        ticketRepository.findAll().forEach(ticket ->
            sendToEmitter(new SeatLockEvent(ticket.getSeat(), ticket.getUserId(), "booked"), emitter)
        );

        // 2. Overlay current Redis locks (transient — these may expire)
        Set<String> keys = redisTemplate.keys(LOCK_PREFIX + "*");
        if (keys != null) {
            keys.forEach(key -> {
                String seatId = key.substring(LOCK_PREFIX.length());
                String userId = redisTemplate.opsForValue().get(key);
                if (userId != null) {
                    sendToEmitter(new SeatLockEvent(seatId, userId, "locked"), emitter);
                }
            });
        }

        return emitter;
    }

    // ──────────────────────────────────────────────
    //  Lock / Unlock / Book operations
    // ──────────────────────────────────────────────

    public boolean lock(String seatId, String userId) {
        String key = LOCK_PREFIX + seatId;
        
        // setIfAbsent behaves like a distributed lock
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, userId, Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(acquired)) {
            publishEvent(new SeatLockEvent(seatId, userId, "locked"));
            return true;
        }

        // If not acquired, check if we already own it
        String existingUserId = redisTemplate.opsForValue().get(key);
        if (userId.equals(existingUserId)) {
            // Refresh TTL
            redisTemplate.expire(key, Duration.ofSeconds(LOCK_TTL_SECONDS));
            return true;
        }

        return false;
    }

    public void unlock(String seatId, String userId) {
        String key = LOCK_PREFIX + seatId;
        String existingUserId = redisTemplate.opsForValue().get(key);
        
        if (userId.equals(existingUserId)) {
            redisTemplate.delete(key);
            publishEvent(new SeatLockEvent(seatId, userId, "available"));
        }
    }

    /**
     * Called by {@link BookingService} AFTER the DB transaction commits successfully.
     * Releases the Redis lock and broadcasts the "booked" SSE event to all clients.
     *
     * Do NOT call this directly — always go through BookingService.book() so that
     * the lock release is coupled to a successful DB persist.
     */
    public void releaseAndBroadcastBooked(String seatId, String userId) {
        String key = LOCK_PREFIX + seatId;
        redisTemplate.delete(key);
        log.info("Lock released post-booking — seat={} user={}", seatId, userId);
        publishEvent(new SeatLockEvent(seatId, userId, "booked"));
    }

    // ──────────────────────────────────────────────
    //  Redis Pub/Sub & SSE Broadcasting
    // ──────────────────────────────────────────────

    /** Publishes event to Redis so ALL backend instances receive it */
    private void publishEvent(SeatLockEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(RedisConfig.SEAT_UPDATES_TOPIC, json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    /** 
     * Called by RedisMessageListenerContainer when a message arrives on the Pub/Sub topic.
     * This receives events from ANY backend instance.
     */
    public void receiveRedisMessage(String message) {
        try {
            SeatLockEvent event = objectMapper.readValue(message, SeatLockEvent.class);
            // Forward the event to all locally connected browsers
            broadcastToLocals(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastToLocals(SeatLockEvent event) {
        localEmitters.forEach(emitter -> sendToEmitter(event, emitter));
    }

    private void sendToEmitter(SeatLockEvent event, SseEmitter emitter) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .name("seat-update")
                    .data(json));
        } catch (IOException e) {
            emitter.completeWithError(e);
            localEmitters.remove(emitter);
        }
    }
}
