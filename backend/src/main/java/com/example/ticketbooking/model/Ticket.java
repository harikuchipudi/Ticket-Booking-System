package com.example.ticketbooking.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing a confirmed ticket booking stored in Neon PostgreSQL.
 *
 * Key constraints:
 * - `seat` is UNIQUE — the database itself prevents double-bookings as a
 *   final safety net beyond the Redis distributed lock.
 * - `userId` ties the booking to the authenticated user (bare string for now;
 *   will become a FK to `users` table when auth is added in Phase 2).
 */
@Entity
@Table(
    name = "tickets",
    uniqueConstraints = @UniqueConstraint(name = "uq_ticket_seat", columnNames = "seat")
)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The event/match name this ticket belongs to. */
    @Column(name = "match_name", nullable = false)
    private String matchName;

    /** Seat identifier, e.g. "A1", "B12". Unique across the event. */
    @Column(nullable = false, unique = true)
    private String seat;

    /** Display name of the person who booked. */
    @Column(name = "customer_name", nullable = false)
    private String customerName;

    /** The user ID who owns this booking. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Current status of the ticket (BOOKED is the only persisted state). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.BOOKED;

    /** When the booking was confirmed. */
    @Column(name = "booked_at", nullable = false, updatable = false)
    private Instant bookedAt = Instant.now();

    /** Ticket price — populated once payment is integrated in Phase 4. */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** Stadium section label (e.g. "North Stand"). */
    @Column(length = 100)
    private String section;

    // ────────────────────────────────────
    //  Constructors
    // ────────────────────────────────────

    public Ticket() {}

    /** Convenience constructor used by BookingService. */
    public Ticket(String matchName, String seat, String customerName, String userId) {
        this.matchName    = matchName;
        this.seat         = seat;
        this.customerName = customerName;
        this.userId       = userId;
        this.status       = TicketStatus.BOOKED;
        this.bookedAt     = Instant.now();
    }

    // ────────────────────────────────────
    //  Getters & Setters
    // ────────────────────────────────────

    public Long getId()                   { return id; }
    public void setId(Long id)            { this.id = id; }

    public String getMatchName()                    { return matchName; }
    public void   setMatchName(String matchName)    { this.matchName = matchName; }

    public String getSeat()               { return seat; }
    public void   setSeat(String seat)    { this.seat = seat; }

    public String getCustomerName()                       { return customerName; }
    public void   setCustomerName(String customerName)    { this.customerName = customerName; }

    public String getUserId()                  { return userId; }
    public void   setUserId(String userId)     { this.userId = userId; }

    public TicketStatus getStatus()                    { return status; }
    public void         setStatus(TicketStatus status) { this.status = status; }

    public Instant getBookedAt()                    { return bookedAt; }
    public void    setBookedAt(Instant bookedAt)    { this.bookedAt = bookedAt; }

    public BigDecimal getPrice()                  { return price; }
    public void       setPrice(BigDecimal price)  { this.price = price; }

    public String getSection()                    { return section; }
    public void   setSection(String section)      { this.section = section; }
}
