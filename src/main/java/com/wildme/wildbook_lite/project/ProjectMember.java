package com.wildme.wildbook_lite.project;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Join entity (project ⇄ user) with extra column `role`.
 *  - We deliberately model this as a first-class entity instead of a
 *    @ManyToMany join table, because the relationship carries data
 *    (the role + audit fields).
 *  - Composite uniqueness is enforced by UNIQUE(project_id, user_id).
 */
@Entity
@Table(
    name = "project_members",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_project_member",
        columnNames = {"project_id", "user_id"}
    )
)
public class ProjectMember extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectRole role;

    public ProjectMember() {}

    public ProjectMember(Long projectId, Long userId, ProjectRole role) {
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }
}
