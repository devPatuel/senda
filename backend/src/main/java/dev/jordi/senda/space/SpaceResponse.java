package dev.jordi.senda.space;

import java.time.Instant;

public record SpaceResponse(
        Long id,
        String name,
        MemberStatus myStatus,
        Long createdBy,
        Instant createdAt) {

    public static SpaceResponse from(Space space, MemberStatus myStatus) {
        return new SpaceResponse(space.getId(), space.getName(), myStatus,
                space.getCreatedBy(), space.getCreatedAt());
    }
}
