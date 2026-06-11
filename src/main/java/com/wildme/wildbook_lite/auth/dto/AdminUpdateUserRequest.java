package com.wildme.wildbook_lite.auth.dto;

import java.util.Set;

import com.wildme.wildbook_lite.auth.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Admin-only partial update of an arbitrary user. Each field is
 * optional; null = leave unchanged.
 *
 * What's deliberately NOT here:
 *   - username — usernames are stable identifiers; renaming breaks
 *     every audit-log entry that references the user. If you really
 *     have to rename, do it via a DBA tool, not a public API.
 *   - currentPassword — admin acting on someone else doesn't have it.
 *     This is why this DTO is distinct from UpdateProfileRequest:
 *     resetting your OWN password requires the current one; an admin
 *     resetting someone ELSE's password does not.
 */
public record AdminUpdateUserRequest(
    @Email String email,
    @Size(min = 8, max = 72) String newPassword,
    Set<Role> roles,
    Boolean enabled
) {}
