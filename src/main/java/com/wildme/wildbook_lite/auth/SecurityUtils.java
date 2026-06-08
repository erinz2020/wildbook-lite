package com.wildme.wildbook_lite.auth;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tiny helper to pull the current user out of the SecurityContext.
 * Use instead of injecting Principal in every controller signature.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<AppPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        if (auth.getPrincipal() instanceof AppPrincipal p) return Optional.of(p);
        return Optional.empty();
    }

    public static Long currentUserId() {
        return currentPrincipal()
            .map(AppPrincipal::getUserId)
            .orElseThrow(() -> new IllegalStateException("No authenticated user in context"));
    }

    public static String currentUsername() {
        return currentPrincipal()
            .map(AppPrincipal::getUsername)
            .orElseThrow(() -> new IllegalStateException("No authenticated user in context"));
    }
}
