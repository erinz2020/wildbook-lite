package com.wildme.wildbook_lite.dto;

import jakarta.validation.constraints.NotNull;

/** Assign an encounter to a user. Pass null userId to clear assignment. */
public record AssignEncounterRequest(
    @NotNull Long userId
) {}
