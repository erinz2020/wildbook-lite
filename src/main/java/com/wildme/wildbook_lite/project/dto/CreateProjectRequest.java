package com.wildme.wildbook_lite.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/projects payload.
 *
 * `organizationId` is optional:
 *   - When supplied, the new project is attached to that org. Caller
 *     must be a member of the org (service-side enforcement); otherwise
 *     the request fails with 403. The org gate then takes effect on
 *     every subsequent ProjectGuard check for this project.
 *   - When null, the project lands as a "legacy" project with no org
 *     scope — only project-level RBAC applies. New code should usually
 *     supply an organizationId; null is the migration / dev-seed escape.
 */
public record CreateProjectRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 2000) String description,
    Long organizationId
) {}
