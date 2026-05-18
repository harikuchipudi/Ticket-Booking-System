package com.example.ticketbooking.model;

import java.time.Instant;

/**
 * In-memory record of who holds a seat lock and when it expires.
 */
public class SeatLock {

    private final String userId;
    private final Instant expiry;

    public SeatLock(String userId, Instant expiry) {
        this.userId = userId;
        this.expiry = expiry;
    }

    public String getUserId() { return userId; }
    public Instant getExpiry() { return expiry; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiry);
    }
}
