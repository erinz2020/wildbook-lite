package com.wildme.wildbook_lite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEncounterRequest(
    @NotNull(message = "projectId is required")
    Long projectId,
    @NotBlank(message = "location is required")
    String location,
    @NotBlank(message = "species is required")
    String species
){}