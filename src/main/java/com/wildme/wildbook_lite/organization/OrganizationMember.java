package com.wildme.wildbook_lite.organization;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Org membership row — same first-class-entity shape as
 * {@link com.wildme.wildbook_lite.project.ProjectMember}.
 *
 * Why first-class entity, not @ManyToMany:
 *   - The relationship carries data (role + audit fields).
 *   - Future per-membership flags (invited_at, last_active_at, etc.)
 *     drop in as columns without a join-table migration.
 *
 * Composite uniqueness: a user may appear at most once per org.
 *
 * Why both `org_id` and `user_id` are indexed (via the composite UK
 * AND a standalone user_id index):
 *   - The UK gives us fast (org, user) lookup on the canonical
 *     permission check.
 *   - The user_id-only index drives "list orgs I'm a member of",
 *     which the org dashboard hits on every page load.
 */
@Entity
@Table(
    name = "organization_members",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_org_member",
        columnNames = {"org_id", "user_id"}
    ),
    indexes = {
        @Index(name = "ix_org_member_user", columnList = "user_id")
    }
)
public class OrganizationMember extends BaseEntity {

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrgRole role;

    public OrganizationMember() {}

    public OrganizationMember(Long orgId, Long userId, OrgRole role) {
        this.orgId = orgId;
        this.userId = userId;
        this.role = role;
    }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public OrgRole getRole() { return role; }
    public void setRole(OrgRole role) { this.role = role; }
}
