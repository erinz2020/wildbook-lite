package com.wildme.wildbook_lite.project.dto;

import java.time.Instant;

import com.wildme.wildbook_lite.project.Project;

public record ProjectResponse(
    Long id,
    String name,
    String description,
    Long ownerUserId,
    Instant createdAt
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
            p.getId(),
            p.getName(),
            p.getDescription(),
            p.getOwnerUserId(),
            p.getCreatedAt()
        );
    }
}
