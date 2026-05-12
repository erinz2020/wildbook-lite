package com.wildme.wildbook_lite.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIndividualRequest(
    @NotBlank(message = "nickname is required")
    String nickname,
    @NotBlank(message = "species is required")
    String species
){}