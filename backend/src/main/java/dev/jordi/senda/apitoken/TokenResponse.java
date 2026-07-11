package dev.jordi.senda.apitoken;

import java.time.Instant;

/** Token metadata exposed to the owner; never carries the clear value. */
public record TokenResponse(Long id, String name, Instant createdAt,
                            Instant lastUsedAt, Instant revokedAt) {

    public static TokenResponse from(ApiToken t) {
        return new TokenResponse(t.getId(), t.getName(), t.getCreatedAt(),
                t.getLastUsedAt(), t.getRevokedAt());
    }
}
