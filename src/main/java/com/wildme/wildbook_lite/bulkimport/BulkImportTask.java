package com.wildme.wildbook_lite.bulkimport;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bulk_import_task", indexes = {
    @Index(name = "ix_bulk_import_task_status", columnList = "status")
})
public class BulkImportTask extends BaseEntity {

    @Column(name = "bulk_import_id", nullable = false, unique = true)
    private UUID bulkImportId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "submitter_user_id")
    private Long submitterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BulkImportStatus status = BulkImportStatus.PENDING;

    private int totalRows;

    private int processedRows;

    private int successCount;

    private int failureCount;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(columnDefinition = "TEXT")
    private String errorsJson;


    public UUID getBulkImportId() { return bulkImportId; }
    public void setBulkImportId(UUID bulkImportId) { this.bulkImportId = bulkImportId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getSubmitterUserId() { return submitterUserId; }
    public void setSubmitterUserId(Long submitterUserId) { this.submitterUserId = submitterUserId; }

    public BulkImportStatus getStatus() { return status; }
    public void setStatus(BulkImportStatus status) { this.status = status; }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getProcessedRows() { return processedRows; }
    public void setProcessedRows(int processedRows) { this.processedRows = processedRows; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public String getErrorsJson() { return errorsJson; }
    public void setErrorsJson(String errorsJson) { this.errorsJson = errorsJson; }
}
