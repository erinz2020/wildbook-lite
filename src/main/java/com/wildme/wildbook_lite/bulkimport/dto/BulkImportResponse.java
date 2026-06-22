package com.wildme.wildbook_lite.bulkimport.dto;

import com.wildme.wildbook_lite.bulkimport.BulkImportStatus;
import com.wildme.wildbook_lite.bulkimport.BulkImportTask;

import java.time.LocalDateTime;
import java.util.UUID;

public record BulkImportResponse(
    Long taskId,
    UUID bulkImportId,
    BulkImportStatus status,
    int totalRows,
    int processedRows,
    int successCount,
    int failureCount,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {
    public static BulkImportResponse from(BulkImportTask task) {
        return new BulkImportResponse(
            task.getId(),
            task.getBulkImportId(),
            task.getStatus(),
            task.getTotalRows(),
            task.getProcessedRows(),
            task.getSuccessCount(),
            task.getFailureCount(),
            task.getStartedAt(),
            task.getFinishedAt()
        );
    }
}