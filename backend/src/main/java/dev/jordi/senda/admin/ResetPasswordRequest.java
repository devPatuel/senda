package dev.jordi.senda.admin;

import dev.jordi.senda.common.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank @Size(min = 8) @MaxBytes(72) String newPassword) {
}
