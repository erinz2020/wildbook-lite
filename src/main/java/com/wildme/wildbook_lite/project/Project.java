package com.wildme.wildbook_lite.project;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects", indexes = {
    @Index(name = "ix_project_org", columnList = "organization_id")
})
public class Project extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 2000)
    private String description;

    /** The user who originally created this project. */
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    /**
     * Owning Organization. Nullable for backwards compat:
     *   - null  → "legacy" project that pre-dates the Organization
     *             feature. ProjectGuard falls back to project-only
     *             RBAC. Existing seeded rows stay reachable.
     *   - !null → ProjectGuard layers an OrgGuard.isMember check on
     *             top of project membership; you must be both an
     *             org member AND a project member.
     *
     * Stored as a plain Long (not @ManyToOne) for the same reason as
     * encounter.submitterUserId — keeps the entity graph free of
     * surprise lazy navigation from permission checks.
     */
    @Column(name = "organization_id")
    private Long organizationId;

    public Project() {}

    public Project(String name, String description, Long ownerUserId) {
        this.name = name;
        this.description = description;
        this.ownerUserId = ownerUserId;
    }

    public Project(String name, String description, Long ownerUserId, Long organizationId) {
        this(name, description, ownerUserId);
        this.organizationId = organizationId;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
}
