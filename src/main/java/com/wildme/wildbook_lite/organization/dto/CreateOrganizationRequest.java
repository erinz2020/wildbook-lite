package com.wildme.wildbook_lite.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/organizations payload.
 *
 * `slug` is optional — when blank the service derives one from `name`
 * (lowercase, alphanumerics + dashes). When supplied, must match the
 * URL-safe pattern; we reject the alternative of silently sanitizing
 * because that hides typos.
 */
public record CreateOrganizationRequest(
    @NotBlank @Size(max = 128)
    String name,

    @Size(max = 64)
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
             message = "slug must be lowercase alphanumerics + dashes (no leading/trailing/double dash)")
    String slug,

    @Size(max = 2000)
    String description
) {}
