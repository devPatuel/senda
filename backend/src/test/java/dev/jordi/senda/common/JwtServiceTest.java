package dev.jordi.senda.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-signing-0123456789";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(24)));

    @Test
    void generatedTokenContainsUserIdAsSubject() {
        String token = jwtService.generateToken(42L);

        assertThat(jwtService.extractUserId(token)).contains(42L);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(jwtService.extractUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtService other = new JwtService(new JwtProperties(
                "another-secret-key-that-is-also-long-enough-for-hs256-9876543210", Duration.ofHours(24)));
        String token = other.generateToken(42L);

        assertThat(jwtService.extractUserId(token)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiring = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-10)));
        String token = expiring.generateToken(42L);

        assertThat(jwtService.extractUserId(token)).isEmpty();
    }
}
