package com.wildme.wildbook_lite.project;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 2000)
    private String description;

    /** The user who originally created this project. */
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    public Project() {}

    public Project(String name, String description, Long ownerUserId) {
        this.name = name;
        this.description = description;
        this.ownerUserId = ownerUserId;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
}
