package com.example.ticketbooking.controller;

import com.example.ticketbooking.model.Ticket;
import com.example.ticketbooking.service.BookingService;
import com.example.ticketbooking.service.SeatLockService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * REST + SSE controller for real-time seat locking and booking.
 *
 * userId is extracted from the validated JWT (@AuthenticationPrincipal),
 * never from the request body — this prevents impersonation.
 *
 * Endpoints:
 *   GET  /api/seats/stream       → SSE (public — EventSource can't set headers)
 *   POST /api/seats/{id}/lock    → acquire distributed Redis lock
 *   POST /api/seats/{id}/unlock  → release lock
 *   POST /api/seats/{id}/book    → atomic persist to Neon + release lock
 */
@RestController
@RequestMapping("/api/seats")
public class SeatLockController {

    private final SeatLockService seatLockService;
    private final BookingService  bookingService;

    public SeatLockController(SeatLockService seatLockService, BookingService bookingService) {
        this.seatLockService = seatLockService;
        this.bookingService  = bookingService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return seatLockService.subscribe();
    }

    @PostMapping("/{seatId}/lock")
    public ResponseEntity<Map<String, Object>> lock(
            @PathVariable String seatId,
            @AuthenticationPrincipal String userId) {

        boolean success = seatLockService.lock(seatId, userId);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.status(409)
                .body(Map.of("success", false, "reason", "Seat already locked by another user"));
    }

    @PostMapping("/{seatId}/unlock")
    public ResponseEntity<Void> unlock(
            @PathVariable String seatId,
            @AuthenticationPrincipal String userId) {

        seatLockService.unlock(seatId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{seatId}/book")
    public ResponseEntity<Object> book(
            @PathVariable String seatId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {

        try {
            Ticket saved = bookingService.book(
                    seatId,
                    userId,  // from JWT — verified identity
                    body.getOrDefault("matchName", "General Admission"),
                    body.getOrDefault("customerName", userId)
            );
            return ResponseEntity.ok(saved);
        } catch (BookingService.SeatNotLockedException | BookingService.SeatAlreadyBookedException ex) {
            return ResponseEntity.status(409)
                    .body(Map.of("success", false, "reason", ex.getMessage()));
        }
    }
}
