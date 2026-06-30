package com.wildme.wildbook_lite.bulkimport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j   // Lombok generates: private static final Logger log = LoggerFactory.getLogger(...)
public class BulkImportRunner {

    private final BulkImportTaskRepository taskRepo;
    private final ObjectMapper objectMapper;
    private final Map<String, RowImporter> importers;

    public BulkImportRunner(BulkImportTaskRepository taskRepo,
                            ObjectMapper objectMapper,
                            List<RowImporter> importerList) {
        this.taskRepo = taskRepo;
        this.objectMapper = objectMapper;
        // Spring injects EVERY RowImporter bean as a List. Index them by the
        // type each one handles, so we can look up the right one at runtime:
        //   {"encounter" -> EncounterRowImporter, "individual" -> ...}
        this.importers = importerList.stream()
            .collect(Collectors.toMap(RowImporter::supportedType, i -> i));
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

        // Pick the importer for this import's target type.
        RowImporter importer = importers.get(payload.targetType());
        if (importer == null) {
            throw new IllegalArgumentException("No importer for type: " + payload.targetType());
        }

        int success = 0;
        int failure = 0;
        List<ValidationError> errors = new ArrayList<>();

        // Per-row try-catch = partial failure tolerance: one bad row does not
        // abort the whole import.
        for (int i = 0; i < rows.size(); i++) {
            try {
                importer.importRow(payload.fieldNames(), rows.get(i));
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
