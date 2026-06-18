package com.wildme.wildbook_lite.organization;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Top-level tenant. An org owns N Projects; projects in different orgs
 * are isolated from each other regardless of project-level membership.
 *
 * Why a separate entity (vs reusing Project):
 *   - Different lifecycle: orgs survive the deletion of any specific
 *     project; ownership of long-lived resources (datasets, API keys
 *     later) hangs off the org, not any one project.
 *   - Different permission scope: org membership is a precondition for
 *     ANY project visibility within it. Two layers of RBAC stacked.
 *
 * Why `slug` is here as a unique string:
 *   - Lets us build user-friendly URLs ("/orgs/wild-me-pacific/..."
 *     when we ever add a frontend).
 *   - Indexed unique constraint makes slug ↔ id lookup O(log n) without
 *     a per-row scan.
 *
 * Why `ownerUserId` is a column even though OrganizationMember already
 * records the same fact:
 *   - Optimistic fast path: "find orgs created by me" is one column
 *     scan vs an N+1 join into the member table.
 *   - The OWNER role row is still the authoritative source — this is
 *     a denormalized cache, and the service keeps them in sync.
 */
@Entity
@Table(
    name = "organizations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_org_slug", columnNames = "slug")
    },
    indexes = {
        @Index(name = "ix_org_owner", columnList = "owner_user_id")
    }
)
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    /**
     * URL-safe handle. Lowercase, dash-separated. Generated from `name`
     * if the client doesn't supply one. Optional but unique.
     */
    @Column(length = 64)
    private String slug;

    @Column(length = 2000)
    private String description;

    /** Denormalized: the original creator. See class javadoc. */
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    public Organization() {}

    public Organization(String name, String slug, String description, Long ownerUserId) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.ownerUserId = ownerUserId;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
}
