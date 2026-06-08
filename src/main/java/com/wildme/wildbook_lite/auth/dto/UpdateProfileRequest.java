package com.wildme.wildbook_lite.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial-update DTO. Each field is optional; null = leave unchanged.
 * No @NotBlank.
 */
public record UpdateProfileRequest(
    @Email String email,
    @Size(min = 8, max = 72) String newPassword,
    @Size(min = 8, max = 72) String currentPassword
) {}
