package com.wildme.wildbook_lite.bulkimport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.wildme.wildbook_lite.bulkimport.dto.ValidationError;

/**
 * The async worker for bulk import. A SEPARATE bean from BulkImportService
 * so the @Async proxy boundary actually applies — if the service called its
 * own @Async method via `this`, the call would bypass the proxy and run
 * synchronously (proxy self-invocation trap).
 *
 * run() is the background entry point: it is deliberately NOT one big
 * transaction. Each status change is persisted on its own (via the
 * repository's per-save commit) so a poller sees PENDING → IMPORTING →
 * COMPLETED progress, and a mid-import crash doesn't roll the marker back
 * to PENDING.
 */
@Component
public class BulkImportRunner {

    private static final Logger log = LoggerFactory.getLogger(BulkImportRunner.class);

    private final BulkImportTaskRepository taskRepo;
    private final ObjectMapper objectMapper;

    public BulkImportRunner(BulkImportTaskRepository taskRepo, ObjectMapper objectMapper) {
        this.taskRepo = taskRepo;
        this.objectMapper = objectMapper;
    }

    /** Background entry point. Runs on a pool thread (see AsyncConfig). */
    @Async("applicationTaskExecutor")
    public void run(Long taskId) {
        log.info("[bulk-import] starting task {}", taskId);
        try {
            if (!markImporting(taskId)) {
                return; // task gone, or not in PENDING (already picked up)
            }

            importRows(taskId);

            markCompleted(taskId);
            log.info("[bulk-import] finished task {}", taskId);
        } catch (Exception e) {
            log.warn("[bulk-import] task {} failed: {}", taskId, e.toString());
            markFailed(taskId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** PENDING → IMPORTING. Returns false if the task is gone or not PENDING. */
    private boolean markImporting(Long taskId) {
        BulkImportTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null || t.getStatus() != BulkImportStatus.PENDING) {
            return false;
        }
        t.setStatus(BulkImportStatus.IMPORTING);
        t.setStartedAt(LocalDateTime.now());
        taskRepo.save(t);
        return true;
    }

    /** IMPORTING → COMPLETED. */
    private void markCompleted(Long taskId) {
        BulkImportTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null) {
            return;
        }
        t.setStatus(BulkImportStatus.COMPLETED);
        t.setFinishedAt(LocalDateTime.now());
        taskRepo.save(t);
    }

    /** any → FAILED, recording the reason. */
    private void markFailed(Long taskId, String message) {
        BulkImportTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null) {
            return;
        }
        t.setStatus(BulkImportStatus.FAILED);
        t.setFinishedAt(LocalDateTime.now());
        t.setErrorsJson(message);
        taskRepo.save(t);
    }

    /** Deserialize the persisted payload and import each row (stubbed work). */
    private void importRows(Long taskId) {
        BulkImportTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null || t.getPayloadJson() == null) {
            return;
        }

        BulkImportPayload payload = fromJson(t.getPayloadJson());
        List<List<String>> rows = payload.rows();
        int expectedCols = payload.fieldNames().size();

        int success = 0;
        int failure = 0;
        List<ValidationError> errors = new ArrayList<>();

        // Per-row try-catch = partial failure tolerance: one bad row does not
        // abort the whole import.
        for (int i = 0; i < rows.size(); i++) {
            try {
                simulateImportRow(rows.get(i), expectedCols);
                success++;
            } catch (Exception e) {
                failure++;
                errors.add(new ValidationError(i, null, "IMPORT_FAILED", e.getMessage()));
            }
        }

        // Persist final progress once the loop is done.
        t.setProcessedRows(rows.size());
        t.setSuccessCount(success);
        t.setFailureCount(failure);
        if (!errors.isEmpty()) {
            t.setErrorsJson(toJson(errors));
        }
        taskRepo.save(t);
    }

    /** Stub for the real "row → Encounter + save" work. Fails blank/short rows. */
    private void simulateImportRow(List<String> row, int expectedCols) {
        if (row.size() != expectedCols) {
            throw new IllegalArgumentException("Column count mismatch");
        }
        for (String cell : row) {
            if (cell == null || cell.isBlank()) {
                throw new IllegalArgumentException("Blank cell");
            }
        }
        // Real implementation: new Encounter(...) + encounterRepo.save(). MVP stubs it.
    }

    private BulkImportPayload fromJson(String json) {
        try {
            return objectMapper.readValue(json, BulkImportPayload.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize payload", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
