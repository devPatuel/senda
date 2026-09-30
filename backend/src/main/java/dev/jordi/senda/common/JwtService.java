package dev.jordi.senda.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final String VERSION_CLAIM = "ver";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
    }

    /** A verified token: who it belongs to and the token version it was issued with. */
    public record Session(Long userId, int tokenVersion) {
    }

    public String generateToken(Long userId) {
        return generateToken(userId, 0);
    }

    public String generateToken(Long userId, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(VERSION_CLAIM, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the user id from a valid token, or empty if the token is
     * malformed, expired or has an invalid signature.
     */
    public Optional<Long> extractUserId(String token) {
        return parse(token).map(Session::userId);
    }

    /**
     * Returns the session carried by a valid token, or empty if the token is
     * malformed, expired or has an invalid signature. A token without a version
     * claim (issued before versions existed) counts as version 0.
     */
    public Optional<Session> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer version = claims.get(VERSION_CLAIM, Integer.class);
            return Optional.of(new Session(Long.parseLong(claims.getSubject()), version == null ? 0 : version));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
