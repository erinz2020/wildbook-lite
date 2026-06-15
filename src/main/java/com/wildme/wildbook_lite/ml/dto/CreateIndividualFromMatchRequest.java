package com.wildme.wildbook_lite.ml.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/ia-tasks/{taskId}/create-individual payload.
 *
 * Sent when none of the candidates match → reviewer is registering a
 * brand-new Individual for the query annotation's encounter.
 *
 * `sex` is a free string for now; we don't have a Sex enum because the
 * underlying Individual entity doesn't either. Adding the enum is a
 * future refactor.
 */
public record CreateIndividualFromMatchRequest(
    @NotBlank @Size(max = 128)
    String nickname,

    @Size(max = 16)
    String sex,

    @Size(max = 2000)
    String remarks
) {}
