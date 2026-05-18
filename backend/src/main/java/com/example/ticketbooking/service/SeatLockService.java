package com.example.ticketbooking.service;

import com.example.ticketbooking.config.RedisConfig;
import com.example.ticketbooking.model.SeatLockEvent;
import com.example.ticketbooking.repository.TicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages real-time seat locking via Redis and SSE broadcasting.
 */
@Service
public class SeatLockService {

    private static final Logger log = LoggerFactory.getLogger(SeatLockService.class);
    private static final long   LOCK_TTL_SECONDS = 5 * 60;
    private static final String LOCK_PREFIX      = "seat:lock:"; // We'll append matchName:seatId

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final TicketRepository    ticketRepository;

    // Group emitters by matchName to avoid broadcasting irrelevant events
    private final Map<String, List<SseEmitter>> matchEmitters = new ConcurrentHashMap<>();

    // ── Metrics ──────────────────────────────────────────────────────────────
    private final Counter lockSuccessCounter;
    private final Counter lockConflictCounter;
    private final Counter lockRefreshCounter;

    public SeatLockService(StringRedisTemplate redisTemplate,
                           TicketRepository ticketRepository,
                           MeterRegistry meterRegistry) {
        this.redisTemplate    = redisTemplate;
        this.ticketRepository = ticketRepository;
        this.objectMapper     = new ObjectMapper();

        this.lockSuccessCounter  = Counter.builder("seat.lock.attempts")
                .tag("result", "success")
                .register(meterRegistry);
        this.lockConflictCounter = Counter.builder("seat.lock.attempts")
                .tag("result", "conflict")
                .register(meterRegistry);
        this.lockRefreshCounter  = Counter.builder("seat.lock.attempts")
                .tag("result", "refresh")
                .register(meterRegistry);

        Gauge.builder("sse.active.connections", matchEmitters, m -> m.values().stream().mapToInt(List::size).sum())
                .description("Total active SSE connections across all matches")
                .register(meterRegistry);
    }

    // ── SSE lifecycle ─────────────────────────────────────────────────────────

    public SseEmitter subscribe(String matchName) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        matchEmitters.computeIfAbsent(matchName, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable onDisconnect = () -> {
            List<SseEmitter> list = matchEmitters.get(matchName);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    matchEmitters.remove(matchName);
                }
            }
        };

        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(e -> onDisconnect.run());

        // 1. Send DB-persisted booked seats for this match (optimized with Redis caching)
        String cacheKey = "stadium:booked:" + matchName;
        Map<Object, Object> cachedSeats = redisTemplate.opsForHash().entries(cacheKey);

        if (cachedSeats == null || cachedSeats.isEmpty()) {
            // Cache miss: load from DB
            List<com.example.ticketbooking.model.Ticket> tickets = ticketRepository.findByMatchName(matchName);
            if (!tickets.isEmpty()) {
                Map<String, String> cacheMap = new java.util.HashMap<>();
                tickets.forEach(ticket -> {
                    cacheMap.put(ticket.getSeat(), ticket.getUserId());
                    sendToEmitter(new SeatLockEvent(matchName, ticket.getSeat(), ticket.getUserId(), "booked"), emitter);
                });
                redisTemplate.opsForHash().putAll(cacheKey, cacheMap);
                redisTemplate.expire(cacheKey, Duration.ofHours(24));
            }
        } else {
            // Cache hit: serve from Redis
            cachedSeats.forEach((seat, user) -> 
                sendToEmitter(new SeatLockEvent(matchName, seat.toString(), user.toString(), "booked"), emitter)
            );
        }

        // 2. Overlay current Redis locks for this match
        String matchPrefix = LOCK_PREFIX + matchName + ":";
        Set<String> keys = redisTemplate.keys(matchPrefix + "*");
        if (keys != null) {
            keys.forEach(key -> {
                String seatId = key.substring(matchPrefix.length());
                String userId = redisTemplate.opsForValue().get(key);
                if (userId != null) {
                    sendToEmitter(new SeatLockEvent(matchName, seatId, userId, "locked"), emitter);
                }
            });
        }
        return emitter;
    }

    // ── Locking operations ────────────────────────────────────────────────────

    @CircuitBreaker(name = "redis", fallbackMethod = "lockFallback")
    public boolean lock(String matchName, String seatId, String userId) {
        String  key      = LOCK_PREFIX + matchName + ":" + seatId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, userId, Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(acquired)) {
            lockSuccessCounter.increment();
            publishEvent(new SeatLockEvent(matchName, seatId, userId, "locked"));
            return true;
        }

        String existingOwner = redisTemplate.opsForValue().get(key);
        if (userId.equals(existingOwner)) {
            redisTemplate.expire(key, Duration.ofSeconds(LOCK_TTL_SECONDS));
            lockRefreshCounter.increment();
            return true;
        }

        lockConflictCounter.increment();
        return false;
    }

    public boolean lockFallback(String matchName, String seatId, String userId, Throwable t) {
        log.error("Redis circuit OPEN — lock rejected for match={} seat={} user={}: {}", matchName, seatId, userId, t.getMessage());
        lockConflictCounter.increment();
        return false;
    }

    public void unlock(String matchName, String seatId, String userId) {
        String key           = LOCK_PREFIX + matchName + ":" + seatId;
        String existingOwner = redisTemplate.opsForValue().get(key);

        if (userId.equals(existingOwner)) {
            redisTemplate.delete(key);
            publishEvent(new SeatLockEvent(matchName, seatId, userId, "available"));
        }
    }

    /**
     * Called by the OutboxProcessor. Guarantees that locks are released,
     * cache is updated, and SSE events are broadcast exactly once per booking.
     */
    public void processBookedEvent(SeatLockEvent event) {
        String matchName = event.getMatchName();
        String seatId = event.getSeatId();
        
        // 1. Delete lock
        redisTemplate.delete(LOCK_PREFIX + matchName + ":" + seatId);
        
        // 2. Update cache
        String cacheKey = "stadium:booked:" + matchName;
        redisTemplate.opsForHash().put(cacheKey, seatId, event.getUserId());
        
        // 3. Broadcast
        publishEvent(event);
    }

    // ── Redis Pub/Sub + SSE ───────────────────────────────────────────────────

    private void publishEvent(SeatLockEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(RedisConfig.SEAT_UPDATES_TOPIC, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SSE event", e);
        }
    }

    public void receiveRedisMessage(String message) {
        try {
            SeatLockEvent event = objectMapper.readValue(message, SeatLockEvent.class);
            List<SseEmitter> emitters = matchEmitters.get(event.getMatchName());
            if (emitters != null) {
                emitters.forEach(emitter -> sendToEmitter(event, emitter));
            }
        } catch (Exception e) {
            log.error("Failed to deserialize Redis message: {}", message, e);
        }
    }

    private void sendToEmitter(SeatLockEvent event, SseEmitter emitter) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().name("seat-update").data(json));
        } catch (IOException e) {
            emitter.completeWithError(e);
            // Removal handled by callbacks
        }
    }
}
