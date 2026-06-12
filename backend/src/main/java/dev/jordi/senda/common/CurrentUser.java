package dev.jordi.senda.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
