package com.example.ticketbooking.model;

import java.time.Instant;

/** Outbound DTO for GET /api/auth/me — never exposes passwordHash */
public class UserProfile {
    private String  userId;
    private String  email;
    private String  displayName;
    private Instant createdAt;

    public UserProfile() {}

    public UserProfile(User user) {
        this.userId      = user.getId().toString();
        this.email       = user.getEmail();
        this.displayName = user.getDisplayName();
        this.createdAt   = user.getCreatedAt();
    }

    public String  getUserId()      { return userId; }
    public String  getEmail()       { return email; }
    public String  getDisplayName() { return displayName; }
    public Instant getCreatedAt()   { return createdAt; }
}
