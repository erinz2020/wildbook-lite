package com.wildme.wildbook_lite.tag;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Tag is scoped to a Project — different projects can reuse the same
 * label ("rare") without colliding. Composite unique key (project_id, name)
 * enforces this.
 */
@Entity
@Table(
    name = "tags",
    uniqueConstraints = @UniqueConstraint(name = "uk_tag_per_project", columnNames = {"project_id", "name"}),
    indexes = @Index(name = "ix_tag_project", columnList = "project_id")
)
public class Tag extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 32)
    private String color;

    public Tag() {}

    public Tag(Long projectId, String name, String color) {
        this.projectId = projectId;
        this.name = name;
        this.color = color;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
