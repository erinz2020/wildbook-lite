package com.wildme.wildbook_lite.auth.dto;

import java.util.Set;

import com.wildme.wildbook_lite.auth.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin-only "create a user" payload.
 *
 * Why a separate DTO from RegisterRequest:
 *   - RegisterRequest was a SELF-registration shape (the request body
 *     literally was the new user's own data). It does NOT contain a
 *     `roles` field, because letting the registrant pick their own role
 *     would be a trivial privilege-escalation.
 *   - AdminCreateUserRequest is filed BY the admin FOR someone else, and
 *     does include `roles` — the admin is allowed to set them.
 *
 * If `roles` is null/empty we default to RESEARCHER. That's the only
 * place the default lives, so the rule is in one place.
 */
public record AdminCreateUserRequest(
    @NotBlank @Size(min = 3, max = 64) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    Set<Role> roles
) {}
