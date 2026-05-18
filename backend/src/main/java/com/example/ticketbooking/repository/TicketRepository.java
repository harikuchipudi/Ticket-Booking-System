package com.example.ticketbooking.repository;

import com.example.ticketbooking.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Ticket persistence.
 * Replaces InMemoryTicketRepository entirely.
 *
 * The UNIQUE constraint on the `seat` column (defined on the entity)
 * acts as the final safety net against double-bookings — even if two
 * concurrent requests slip past the Redis lock, only one can commit.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Used by BookingService to check for an existing booking before persisting.
     * Also used by SeatLockService to verify a seat's current DB state.
     */
    Optional<Ticket> findBySeat(String seat);

    /**
     * Returns all tickets booked by a specific user.
     * Powers the "My Bookings" endpoint: GET /api/tickets/my/{userId}
     */
    List<Ticket> findByUserId(String userId);

    /**
     * Returns all booked seats for a given match/event.
     * Useful for rendering the seat map with pre-booked seats on page load.
     */
    List<Ticket> findByMatchName(String matchName);
}
