package com.wildme.wildbook_lite.bulkimport;

import org.springframework.stereotype.Service;

import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResponse;

/**
 * Orchestrates bulk import. Skeleton for now — Phase 4 implements the
 * real logic (validate → persist BulkImportTask → kick off async work).
 */
@Service
public class BulkImportService {

    /** POST handler delegates here: validate the payload, create a task, return it. */
    public BulkImportResponse submit(BulkImportRequest req) {
        throw new UnsupportedOperationException("Phase 4: not implemented yet");
    }

    /** GET handler delegates here: load the task by id and map it to a response. */
    public BulkImportResponse getStatus(Long taskId) {
        throw new UnsupportedOperationException("Phase 4: not implemented yet");
    }
}
