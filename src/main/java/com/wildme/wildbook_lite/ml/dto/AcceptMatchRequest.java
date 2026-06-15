package com.wildme.wildbook_lite.ml.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/ia-tasks/{taskId}/accept payload.
 *
 * `candidateId` identifies WHICH candidate from the result list the
 * reviewer accepted (vs picking by individualId — multiple candidates
 * could in theory point at the same individual; the candidate row is
 * the unambiguous selection).
 */
public record AcceptMatchRequest(
    @NotNull Long candidateId,

    @Size(max = 2000)
    String remarks
) {}
