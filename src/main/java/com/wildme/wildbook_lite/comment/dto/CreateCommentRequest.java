package com.wildme.wildbook_lite.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
    @NotBlank @Size(max = 2000) String body
) {}
