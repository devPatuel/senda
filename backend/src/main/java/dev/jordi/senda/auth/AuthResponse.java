package dev.jordi.senda.auth;

public record AuthResponse(String token, UserDto user) {
}
