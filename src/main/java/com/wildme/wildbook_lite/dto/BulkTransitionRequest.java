package com.wildme.wildbook_lite.dto;

import java.util.List;

import com.wildme.wildbook_lite.encounter.EncounterStatus;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BulkTransitionRequest(
    @NotEmpty @Size(max = 500, message = "max 500 ids per batch") List<Long> ids,
    @NotNull EncounterStatus toStatus
) {}
