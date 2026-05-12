package com.wildme.wildbook_lite.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateObserverRequest(
    @NotBlank(message = "name is required")
    String name,
    String email,
    String organization
) {}
