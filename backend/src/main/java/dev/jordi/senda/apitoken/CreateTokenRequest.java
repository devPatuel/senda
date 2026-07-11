package dev.jordi.senda.apitoken;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTokenRequest(@NotBlank @Size(max = 80) String name) {
}
