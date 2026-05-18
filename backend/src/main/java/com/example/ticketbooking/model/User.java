package com.example.ticketbooking.model;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for registered users stored in Neon PostgreSQL.
 * Password is stored as a BCrypt hash — never in plain text.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public User() {}

    public User(String email, String passwordHash, String displayName) {
        this.email        = email;
        this.passwordHash = passwordHash;
        this.displayName  = displayName;
        this.createdAt    = Instant.now();
    }

    public UUID    getId()           { return id; }
    public String  getEmail()        { return email; }
    public String  getPasswordHash() { return passwordHash; }
    public String  getDisplayName()  { return displayName; }
    public Instant getCreatedAt()    { return createdAt; }

    public void setEmail(String email)              { this.email = email; }
    public void setPasswordHash(String passwordHash){ this.passwordHash = passwordHash; }
    public void setDisplayName(String displayName)  { this.displayName = displayName; }
}
