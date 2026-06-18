package com.wildme.wildbook_lite.organization.dto;

import com.wildme.wildbook_lite.organization.OrgRole;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/organizations/{id}/members payload.
 *
 * Upsert semantics on the service side: if the user is already a
 * member, their role is overwritten. The service refuses to demote
 * the only OWNER (last-owner protection).
 */
public record AddOrgMemberRequest(
    @NotNull Long userId,
    @NotNull OrgRole role
) {}
