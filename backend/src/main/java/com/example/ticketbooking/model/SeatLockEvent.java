package com.example.ticketbooking.model;

/**
 * Broadcast payload sent to all SSE listeners when a seat status changes.
 */
public class SeatLockEvent {

    private String matchName;
    private String seatId;
    private String userId;   // who triggered the change
    private String status;   // "locked" | "available" | "booked"

    public SeatLockEvent() {}

    public SeatLockEvent(String matchName, String seatId, String userId, String status) {
        this.matchName = matchName;
        this.seatId = seatId;
        this.userId = userId;
        this.status = status;
    }

    public String getMatchName() { return matchName; }
    public String getSeatId()  { return seatId; }
    public String getUserId()  { return userId; }
    public String getStatus()  { return status; }

    public void setMatchName(String matchName) { this.matchName = matchName; }
    public void setSeatId(String seatId)   { this.seatId = seatId; }
    public void setUserId(String userId)   { this.userId = userId; }
    public void setStatus(String status)   { this.status = status; }
}
