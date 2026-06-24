package dev.jordi.senda.auth;

import dev.jordi.senda.common.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        // Same 72-byte BCrypt limit as register: no stored password can ever be longer
        @NotBlank @MaxBytes(72) String password) {
}
