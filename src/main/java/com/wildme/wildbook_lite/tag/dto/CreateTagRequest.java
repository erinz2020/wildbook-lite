package com.wildme.wildbook_lite.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 32) @Pattern(regexp = "^(#[0-9A-Fa-f]{3,8})?$",
        message = "color must be a hex like #1a2b3c") String color
) {}
