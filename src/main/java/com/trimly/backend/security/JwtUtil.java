package com.trimly.backend.security;

import com.trimly.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${trimly.jwt.secret}") String secret,
            @Value("${trimly.jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID userId, String email, String role, List<UUID> shopIds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        List<String> shopIdStrings = shopIds.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("role", role)
                .claim("shopIds", shopIdStrings)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).get("userId", String.class));
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<UUID> extractShopIds(String token) {
        List<String> shopIdStrings = parseClaims(token).get("shopIds", List.class);
        return shopIdStrings.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    public boolean isTokenExpired(String token) {
        try {
            return parseClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

}
