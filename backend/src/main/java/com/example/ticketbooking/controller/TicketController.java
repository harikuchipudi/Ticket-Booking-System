package com.example.ticketbooking.controller;

import com.example.ticketbooking.model.Ticket;
import com.example.ticketbooking.repository.TicketRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for querying persisted ticket records from Neon PostgreSQL.
 *
 * Endpoints:
 *   GET /api/tickets               → all tickets (admin)
 *   GET /api/tickets/my            → current user's tickets (from JWT)
 *   GET /api/tickets/health        → liveness check
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;

    public TicketController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping
    public List<Ticket> all() {
        return ticketRepository.findAll();
    }

    /**
     * Returns tickets for the currently authenticated user.
     * userId comes from the JWT — no path param needed.
     */
    @GetMapping("/my")
    public List<Ticket> myTickets(@AuthenticationPrincipal String userId) {
        return ticketRepository.findByUserId(userId);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }
}
