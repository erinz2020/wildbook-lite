package com.wildme.wildbook_lite.organization.dto;

import com.wildme.wildbook_lite.organization.OrgRole;
import com.wildme.wildbook_lite.organization.Organization;

/**
 * Detail response. `myRole` is the caller's role in this org, surfaced
 * here so the UI can render owner-only buttons without a second call.
 * Null when the caller is not a member (shouldn't happen because the
 * endpoint requires canRead, but null-safe anyway).
 */
public record OrganizationResponse(
    Long id,
    String name,
    String slug,
    String description,
    Long ownerUserId,
    OrgRole myRole
) {

    public static OrganizationResponse from(Organization o, OrgRole myRole) {
        return new OrganizationResponse(
            o.getId(),
            o.getName(),
            o.getSlug(),
            o.getDescription(),
            o.getOwnerUserId(),
            myRole
        );
    }
}
