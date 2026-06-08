package com.wildme.wildbook_lite.project.dto;

import com.wildme.wildbook_lite.project.ProjectRole;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
    @NotNull Long userId,
    @NotNull ProjectRole role
) {}
