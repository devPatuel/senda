package dev.jordi.senda.auth;

import dev.jordi.senda.common.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt rejects inputs over 72 UTF-8 bytes; validate here so the client gets a 400, not a 500
        @NotBlank @Size(min = 8) @MaxBytes(72) String password,
        @NotBlank @Size(max = 255) String name) {
}
