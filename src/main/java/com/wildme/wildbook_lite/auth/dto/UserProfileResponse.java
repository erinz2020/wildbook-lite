package com.wildme.wildbook_lite.auth.dto;

import java.time.Instant;
import java.util.Set;

import com.wildme.wildbook_lite.auth.Role;
import com.wildme.wildbook_lite.auth.User;

/** Safe view: never includes passwordHash. */
public record UserProfileResponse(
    Long id,
    String username,
    String email,
    Set<Role> roles,
    boolean enabled,
    Instant createdAt
) {
    public static UserProfileResponse from(User u) {
        return new UserProfileResponse(
            u.getId(), u.getUsername(), u.getEmail(), u.getRoles(),
            u.isEnabled(), u.getCreatedAt()
        );
    }

    /** Limited public view: drops email + roles + enabled flag. */
    public static UserProfileResponse publicView(User u) {
        return new UserProfileResponse(
            u.getId(), u.getUsername(), null, Set.of(), true, u.getCreatedAt()
        );
    }
}
