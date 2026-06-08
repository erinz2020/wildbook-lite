package com.wildme.wildbook_lite.tag.dto;

import com.wildme.wildbook_lite.tag.Tag;

public record TagResponse(Long id, Long projectId, String name, String color) {
    public static TagResponse from(Tag t) {
        return new TagResponse(t.getId(), t.getProjectId(), t.getName(), t.getColor());
    }
}
