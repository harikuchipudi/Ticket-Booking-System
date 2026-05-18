package com.example.ticketbooking.service;

import com.example.ticketbooking.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

/**
 * Handles JWT generation and validation using JJWT 0.11.5 (HS256).
 *
 * The JWT payload contains:
 *   sub         → user UUID (used as principal in SecurityContext)
 *   email       → user email
 *   displayName → user display name
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /** Generates a signed JWT for the given user. */
    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("displayName", user.getDisplayName())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Extracts the user UUID (subject) from a token. */
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Extracts the email claim from a token. */
    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    /** Returns true if the token is structurally valid and not expired. */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
