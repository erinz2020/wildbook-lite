package com.wildme.wildbook_lite.dto;

import java.util.List;

/**
 * "Best-effort" import response: report what succeeded, what failed,
 * and why. Lets the client retry only the broken rows.
 */
public record ImportResult(
    int totalRows,
    int succeeded,
    int failed,
    List<RowError> errors
) {
    public record RowError(int rowNumber, String reason) {}
}
