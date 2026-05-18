package com.example.ticketbooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory sliding-window rate limiter.
 *
 * Tracks how many seat lock attempts each userId has made in the current
 * 1-minute window. The counter map is wiped every 60 seconds via @Scheduled.
 *
 * Limit: 20 lock attempts per user per minute.
 * This is intentionally in-memory (resets on restart) — sufficient for
 * preventing accidental abuse. Upgrade to Redis-backed for multi-instance prod.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final int MAX_LOCKS_PER_MINUTE = 20;

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * Returns true if the userId is still within their rate limit.
     * Increments the counter on every call.
     */
    public boolean allowLockAttempt(String userId) {
        AtomicInteger count = counters.computeIfAbsent(userId, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();

        if (current > MAX_LOCKS_PER_MINUTE) {
            log.warn("Rate limit exceeded — userId={} attempts={}", userId, current);
            return false;
        }
        return true;
    }

    /** Resets all counters every 60 seconds */
    @Scheduled(fixedRate = 60_000)
    public void resetCounters() {
        int size = counters.size();
        counters.clear();
        if (size > 0) {
            log.debug("Rate limiter counters reset — {} users tracked", size);
        }
    }
}
