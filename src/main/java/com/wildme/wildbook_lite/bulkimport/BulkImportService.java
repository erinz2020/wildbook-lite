package com.wildme.wildbook_lite.bulkimport;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResponse;
import com.wildme.wildbook_lite.bulkimport.dto.ValidationError;

import com.wildme.wildbook_lite.exception.BulkImportException;
import com.wildme.wildbook_lite.exception.DuplicateBulkImportException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates bulk import. Skeleton for now — Phase 4 implements the
 * real logic (validate → persist BulkImportTask → kick off async work).
 */

@Service
public class BulkImportService {

    private static final Set<String> REQUIRED_FIELDS = Set.of("species", "datetime", "location"); // Example required fields
    private final BulkImportTaskRepository taskRepo;    
    private static final double MAX_ROW_FAILURE_RATE = 0.5; // Example threshold for row-level validation failures
    private final ObjectMapper objectMapper;
    private final BulkImportRunner runner;

    public BulkImportService(BulkImportTaskRepository taskRepo, 
    ObjectMapper objectMapper,
    BulkImportRunner runner) {
        this.taskRepo = taskRepo;
        this.objectMapper = objectMapper;
        this.runner = runner;
    }

    /** POST handler delegates here: validate the payload, create a task, return it. */
    @Transactional
    public BulkImportResponse submit(BulkImportRequest req) {
       if(taskRepo.findByBulkImportId(req.bulkImportId()).isPresent()) {
           throw new DuplicateBulkImportException("A task with bulkImportId " + req.bulkImportId() + " already exists");
       }

        validateTableLevel(req);
        validateRequiredFields(req);

        List<ValidationError> rowErrors = validateRows(req);
        double failureRate = (double) rowErrors.size() / req.rows().size();

        if(failureRate > MAX_ROW_FAILURE_RATE) {
            throw new BulkImportValidationException(rowErrors);
        }

       BulkImportTask task = new BulkImportTask();
        task.setBulkImportId(req.bulkImportId());
        task.setProjectId(req.projectId());
        task.setTotalRows(req.rows().size());
        task.setPayloadJson(toJson(new BulkImportPayload(req.fieldNames(), req.rows())));

        if(!rowErrors.isEmpty()) {
            task.setErrorsJson(toJson(rowErrors));
        }

        try{

            BulkImportTask saved = taskRepo.save(task);
            runner.run(saved.getId()); // Kick off async processing
            return BulkImportResponse.from(saved);
        } catch(DataIntegrityViolationException e) {
            throw new DuplicateBulkImportException("Bulk import already exists: " + req.bulkImportId());
        }

    }

    private void validateTableLevel(BulkImportRequest req) {
        Set<String> fieldNames = new HashSet<>(req.fieldNames());

        if(fieldNames.size() != req.fieldNames().size()) {
            throw new BulkImportException("Duplicate field names found in request");
        }
    }

    private void validateRequiredFields(BulkImportRequest req) {

        if(!req.fieldNames().containsAll(REQUIRED_FIELDS)) {
            throw new BulkImportException("Missing required fields in request");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to json", e);
        }
    }

    private List<ValidationError> validateRows(BulkImportRequest req) {
         List<ValidationError> errors = new ArrayList<>();

         int expectedCols = req.fieldNames().size();

         List<List<String>> rows = req.rows();
         for(int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if(row.size() != req.fieldNames().size()) {
                errors.add(new ValidationError(
                    i,
                    null,
                    "COLUMN_COUNT_MISMATCH",
                    "Expected " + expectedCols + " columns but got " + row.size() 
                ));
            }
         }

         return errors;
    }

    /** GET handler delegates here: load the task by id and map it to a response. */
    @Transactional(readOnly = true)
    public BulkImportResponse getStatus(UUID bulkImportId) {

        return taskRepo.findByBulkImportId(bulkImportId)
        .map(task -> BulkImportResponse.from(task))
        .orElseThrow(() -> new NotFoundException("Bulk import not found"));
    }
}
