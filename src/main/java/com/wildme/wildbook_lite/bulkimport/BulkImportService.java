package com.wildme.wildbook_lite.bulkimport;

import org.springframework.stereotype.Service;

import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResponse;

import com.wildme.wildbook_lite.exception.BulkImportException;
import com.wildme.wildbook_lite.exception.DuplicateBulkImportException;
import com.wildme.wildbook_lite.exception.NotFoundException;

import java.util.UUID;

/**
 * Orchestrates bulk import. Skeleton for now — Phase 4 implements the
 * real logic (validate → persist BulkImportTask → kick off async work).
 */
@Service
public class BulkImportService {

    private final BulkImportTaskRepository taskRepo;    

    public BulkImportService(BulkImportTaskRepository taskRepo) {
        this.taskRepo = taskRepo;
    }

    /** POST handler delegates here: validate the payload, create a task, return it. */
    public BulkImportResponse submit(BulkImportRequest req) {
       if(taskRepo.findByBulkImportId(req.bulkImportId()).isPresent()) {
           throw new DuplicateBulkImportException("A task with bulkImportId " + req.bulkImportId() + " already exists");
       }

       BulkImportTask task = new BulkImportTask();
        task.setBulkImportId(req.bulkImportId());
        task.setProjectId(req.projectId());
        task.setTotalRows(req.rows().size());

        BulkImportTask saved = taskRepo.save(task);
        
        return BulkImportResponse.from(saved);

    }

    /** GET handler delegates here: load the task by id and map it to a response. */
    public BulkImportResponse getStatus(UUID bulkImportId) {

        return taskRepo.findByBulkImportId(bulkImportId)
        .map(task -> BulkImportResponse.from(task))
        .orElseThrow(() -> new NotFoundException("Bulk import not found"));
    }
}
