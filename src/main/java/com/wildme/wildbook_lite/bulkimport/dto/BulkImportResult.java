package com.wildme.wildbook_lite.bulkimport.dto;

import java.util.List;

/**
 * Aggregate report returned to the caller.
 *
 * Caller gets exact-position feedback (rowIndex on both lists), counts
 * for a quick summary toast, and a flag for "anything created" so the
 * UI can prompt the user to review auto-created reference rows.
 */
public record BulkImportResult(
    int totalRows,
    int successCount,
    int failureCount,
    int taxonomyAutoCreatedCount,
    int observerAutoCreatedCount,
    List<RowSuccess> created,
    List<RowFailure> failed
) {}
