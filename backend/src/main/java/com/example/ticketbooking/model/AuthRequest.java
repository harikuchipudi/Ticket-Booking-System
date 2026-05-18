package com.example.ticketbooking.model;

/** Inbound DTO for /api/auth/register and /api/auth/login */
public class AuthRequest {
    private String email;
    private String password;
    private String displayName; // only used for register

    public AuthRequest() {}

    public String getEmail()       { return email; }
    public String getPassword()    { return password; }
    public String getDisplayName() { return displayName; }

    public void setEmail(String email)             { this.email = email; }
    public void setPassword(String password)       { this.password = password; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
