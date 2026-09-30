package dev.jordi.senda.auth;

import dev.jordi.senda.common.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @MaxBytes(72) String currentPassword,
        // Same rules as sign-up: at least 8 characters, at most BCrypt's 72 bytes
        @NotBlank @Size(min = 8) @MaxBytes(72) String newPassword) {
}
