package com.wildme.wildbook_lite.ml.dto;

import jakarta.validation.constraints.Size;

/**
 * POST /api/ia-tasks/{taskId}/skip payload.
 *
 * Reviewer looked at the candidates and decided not to assign anything
 * (e.g., photos too blurry, ambiguous, will come back to it later).
 * Records the audit fact + optional reason.
 */
public record SkipMatchRequest(
    @Size(max = 2000) String remarks
) {}
