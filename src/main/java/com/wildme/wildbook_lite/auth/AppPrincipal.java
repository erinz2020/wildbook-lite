package com.wildme.wildbook_lite.auth;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Custom Authentication principal so we can stuff `userId` into the
 * SecurityContext (Spring's default User only carries username).
 * Useful so controllers/services can read `currentUserId` without
 * an extra DB round-trip.
 */
public final class AppPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppPrincipal(Long userId, String username, String passwordHash,
                        boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    public Long getUserId() { return userId; }

    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
}
