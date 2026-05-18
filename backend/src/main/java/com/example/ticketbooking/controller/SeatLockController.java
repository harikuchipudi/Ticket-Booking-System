package com.example.ticketbooking.controller;

import com.example.ticketbooking.model.Ticket;
import com.example.ticketbooking.service.BookingService;
import com.example.ticketbooking.service.RateLimiterService;
import com.example.ticketbooking.service.SeatLockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * REST + SSE controller for real-time seat locking and booking.
 *
 * Security:
 *   - userId comes from @AuthenticationPrincipal (JWT) — never from the request body
 *   - lock() is rate-limited to 20 attempts per user per minute (429 if exceeded)
 *   - Redis circuit breaker on lock/unlock — fails safely (false) rather than throwing
 */
@RestController
@RequestMapping("/api/seats")
public class SeatLockController {

    private final SeatLockService   seatLockService;
    private final BookingService    bookingService;
    private final RateLimiterService rateLimiterService;

    public SeatLockController(SeatLockService seatLockService,
                              BookingService bookingService,
                              RateLimiterService rateLimiterService) {
        this.seatLockService    = seatLockService;
        this.bookingService     = bookingService;
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping(value = "/{matchName}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String matchName) {
        return seatLockService.subscribe(matchName);
    }

    @PostMapping("/{matchName}/{seatId}/lock")
    public ResponseEntity<Map<String, Object>> lock(
            @PathVariable String matchName,
            @PathVariable String seatId,
            @AuthenticationPrincipal String userId) {

        if (!rateLimiterService.allowLockAttempt(userId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("success", false, "reason", "Too many lock attempts. Please wait a moment."));
        }

        boolean success = seatLockService.lock(matchName, seatId, userId);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("success", false, "reason", "Seat already locked by another user"));
    }

    @PostMapping("/{matchName}/{seatId}/unlock")
    public ResponseEntity<Void> unlock(
            @PathVariable String matchName,
            @PathVariable String seatId,
            @AuthenticationPrincipal String userId) {

        seatLockService.unlock(matchName, seatId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{matchName}/{seatId}/book")
    public ResponseEntity<Object> book(
            @PathVariable String matchName,
            @PathVariable String seatId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {

        try {
            Ticket saved = bookingService.book(
                    seatId,
                    userId,
                    matchName,
                    body.getOrDefault("customerName", userId)
            );
            return ResponseEntity.ok(saved);
        } catch (BookingService.SeatNotLockedException | BookingService.SeatAlreadyBookedException ex) {
            return ResponseEntity.status(409)
                    .body(Map.of("success", false, "reason", ex.getMessage()));
        }
    }
}
