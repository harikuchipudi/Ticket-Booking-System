package com.example.ticketbooking.model;

/**
 * Broadcast payload sent to all SSE listeners when a seat status changes.
 */
public class SeatLockEvent {

    private String seatId;
    private String userId;   // who triggered the change
    private String status;   // "locked" | "available" | "booked"

    public SeatLockEvent() {}

    public SeatLockEvent(String seatId, String userId, String status) {
        this.seatId = seatId;
        this.userId = userId;
        this.status = status;
    }

    public String getSeatId()  { return seatId; }
    public String getUserId()  { return userId; }
    public String getStatus()  { return status; }

    public void setSeatId(String seatId)   { this.seatId = seatId; }
    public void setUserId(String userId)   { this.userId = userId; }
    public void setStatus(String status)   { this.status = status; }
}
