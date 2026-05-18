package com.example.ticketbooking.model;

/** Outbound DTO returned by /api/auth/register and /api/auth/login */
public class AuthResponse {
    private String token;
    private String userId;
    private String displayName;
    private long   expiresIn; // seconds

    public AuthResponse() {}

    public AuthResponse(String token, String userId, String displayName, long expiresIn) {
        this.token       = token;
        this.userId      = userId;
        this.displayName = displayName;
        this.expiresIn   = expiresIn;
    }

    public String getToken()       { return token; }
    public String getUserId()      { return userId; }
    public String getDisplayName() { return displayName; }
    public long   getExpiresIn()   { return expiresIn; }
}
