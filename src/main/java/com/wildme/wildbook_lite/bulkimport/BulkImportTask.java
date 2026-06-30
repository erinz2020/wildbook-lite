package com.wildme.wildbook_lite.bulkimport;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bulk_import_task", indexes = {
    @Index(name = "ix_bulk_import_task_status", columnList = "status")
})
@Getter   // Lombok generates a getXxx() for every non-static field at compile time
@Setter   // Lombok generates a setXxx() for every non-static field
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

    @Column(columnDefinition = "TEXT")
    private String payloadJson;
}
