package dev.jordi.senda.apitoken;

import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiTokenService {

    public static final String PREFIX = "senda_pat_";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final ApiTokenRepository repository;

    public ApiTokenService(ApiTokenRepository repository) {
        this.repository = repository;
    }

    /** Generates a token, stores ONLY its hash, and returns the clear value once. */
    @Transactional
    public GeneratedToken generate(Long userId, String name) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = PREFIX + B64.encodeToString(bytes);
        ApiToken saved = repository.save(new ApiToken(userId, sha256(raw), name.trim()));
        return new GeneratedToken(saved.getId(), saved.getName(), raw);
    }

    /** Validates a personal token and returns its owner, bumping last_used_at. */
    @Transactional
    public Optional<Long> resolveUserId(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return repository.findByTokenHash(sha256(rawToken))
                .filter(t -> !t.isRevoked())
                .map(t -> {
                    t.setLastUsedAt(Instant.now());
                    return t.getUserId();
                });
    }

    @Transactional(readOnly = true)
    public List<TokenResponse> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(TokenResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(Long userId, Long id) {
        ApiToken token = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Token not found"));
        if (!token.isRevoked()) {
            token.setRevokedAt(Instant.now());
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never on a JVM
        }
    }

    /** Result of creating a token: carries the clear value, shown only once. */
    public record GeneratedToken(Long id, String name, String value) {
    }
}
