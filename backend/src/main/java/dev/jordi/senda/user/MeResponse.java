package dev.jordi.senda.user;

public record MeResponse(Long id, String email, String name, boolean admin) {
}
