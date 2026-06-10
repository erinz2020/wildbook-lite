package com.wildme.wildbook_lite.dto;

import com.wildme.wildbook_lite.encounter.EncounterStatus;

import jakarta.validation.constraints.NotNull;

public record TransitionEncounterRequest(
    @NotNull EncounterStatus toStatus
) {}
