package com.wildme.wildbook_lite.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateEncounterRequest(
    @NotBlank(message = "location is required")
    String location,
    @NotBlank(message = "species is required")
    String species
){}