package com.wildme.wildbook_lite.dto;

import jakarta.validation.constraints.NotNull;

public record CreateSightingRequest(
    Long encounterId,
    Long observerId,
    String notes
) {}
