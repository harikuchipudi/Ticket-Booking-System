package com.example.ticketbooking.service;

import com.example.ticketbooking.model.AuthResponse;
import com.example.ticketbooking.model.AuthRequest;
import com.example.ticketbooking.model.User;
import com.example.ticketbooking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login, returning a signed JWT on success.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final long TOKEN_EXPIRY_SECONDS = 86_400L; // 24 hours

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService      jwtService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
    }

    @Transactional
    public AuthResponse register(AuthRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new EmailAlreadyUsedException("Email is already registered: " + req.getEmail());
        }

        String hash = passwordEncoder.encode(req.getPassword());
        User user = new User(req.getEmail(), hash,
                req.getDisplayName() != null ? req.getDisplayName() : req.getEmail());
        user = userRepository.save(user);

        log.info("User registered — email={} id={}", user.getEmail(), user.getId());
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId().toString(), user.getDisplayName(), TOKEN_EXPIRY_SECONDS);
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("User logged in — email={} id={}", user.getEmail(), user.getId());
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId().toString(), user.getDisplayName(), TOKEN_EXPIRY_SECONDS);
    }

    public User findById(String userId) {
        return userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    // ── Domain exceptions ─────────────────────────────────────────────────────

    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException(String msg) { super(msg); }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String msg) { super(msg); }
    }
}
