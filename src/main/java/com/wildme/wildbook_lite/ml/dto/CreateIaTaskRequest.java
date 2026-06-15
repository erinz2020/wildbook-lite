package com.wildme.wildbook_lite.ml.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/ia-tasks payload — minimal. Algorithm choice is server-side
 * for now (only the stub matcher exists). Add `String algorithm` here
 * when we want clients to pick.
 */
public record CreateIaTaskRequest(
    @NotNull Long annotationId
) {}
