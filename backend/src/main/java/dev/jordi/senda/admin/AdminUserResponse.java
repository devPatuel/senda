package dev.jordi.senda.admin;

import dev.jordi.senda.user.User;

import java.time.Instant;

/** Account data only: the admin panel never exposes anyone's finances. */
public record AdminUserResponse(Long id, String email, String name, Instant createdAt, boolean admin) {

    static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getCreatedAt(), user.isAdmin());
    }
}
