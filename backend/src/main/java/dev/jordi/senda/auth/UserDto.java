package dev.jordi.senda.auth;

import dev.jordi.senda.user.User;

public record UserDto(Long id, String email, String name) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getName());
    }
}
