package com.example.ticketbooking.controller;

import com.example.ticketbooking.model.AuthRequest;
import com.example.ticketbooking.model.AuthResponse;
import com.example.ticketbooking.model.User;
import com.example.ticketbooking.model.UserProfile;
import com.example.ticketbooking.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints:
 *   POST /api/auth/register  → create user, return JWT
 *   POST /api/auth/login     → verify credentials, return JWT
 *   GET  /api/auth/me        → verify token is valid, return user profile
 *
 * /me is the second step in the Angular concatMap sign-in chain:
 *   login → (concatMap) → /me → (concatMap) → connect SSE
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest req) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(req));
        } catch (UserService.EmailAlreadyUsedException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(null); // 409 - email taken
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        try {
            return ResponseEntity.ok(userService.login(req));
        } catch (UserService.InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * Called by Angular's concatMap chain immediately after storing the token.
     * Returns the authenticated user's profile — confirms the token works.
     * The userId is extracted from the JWT by JwtAuthFilter, not from the DB query.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfile> me(@AuthenticationPrincipal String userId) {
        User user = userService.findById(userId);
        return ResponseEntity.ok(new UserProfile(user));
    }
}
