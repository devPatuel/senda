package dev.jordi.senda.common;

import dev.jordi.senda.apitoken.ApiTokenDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Helper to read the authenticated user id placed in the security context
 * by {@link JwtAuthFilter}.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return userId;
    }

    /** Name of the personal access token behind this request; empty for a web session. */
    public static Optional<String> tokenName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getDetails() instanceof ApiTokenDetails details
                ? Optional.of(details.name())
                : Optional.empty();
    }
}
