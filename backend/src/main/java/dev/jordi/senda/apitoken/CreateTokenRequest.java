package dev.jordi.senda.apitoken;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code scope} is optional: without it the token is QUICK, as every token was before scopes. */
public record CreateTokenRequest(@NotBlank @Size(max = 80) String name, TokenScope scope) {
}
