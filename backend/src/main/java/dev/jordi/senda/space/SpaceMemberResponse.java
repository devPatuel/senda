package dev.jordi.senda.space;

public record SpaceMemberResponse(
        Long userId,
        String email,
        String name,
        MemberStatus status) {
}
