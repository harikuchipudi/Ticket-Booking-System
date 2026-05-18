package com.example.ticketbooking.model;

/**
 * Lifecycle status of a ticket/seat.
 *
 * LOCKED  → a user has reserved the seat temporarily (Redis TTL-backed).
 * BOOKED  → payment confirmed and ticket persisted to the database.
 */
public enum TicketStatus {
    LOCKED,
    BOOKED
}
