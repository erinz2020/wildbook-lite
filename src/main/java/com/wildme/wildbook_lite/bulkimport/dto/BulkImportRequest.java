package com.wildme.wildbook_lite.bulkimport.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BulkImportRequest(
    @NotNull UUID bulkImportId,
    Long projectId,
    @NotBlank String targetType,
    @NotEmpty List<String> fieldNames,
    @NotEmpty List<List<String>> rows,
    boolean processInBackground
) {
}
