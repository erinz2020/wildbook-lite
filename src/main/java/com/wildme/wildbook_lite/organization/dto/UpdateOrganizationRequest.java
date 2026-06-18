package com.wildme.wildbook_lite.organization.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
    @Size(max = 128) String name,

    @Size(max = 64)
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
             message = "slug must be lowercase alphanumerics + dashes")
    String slug,

    @Size(max = 2000) String description
) {}
