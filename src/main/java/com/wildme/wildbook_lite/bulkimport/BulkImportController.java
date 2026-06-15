package com.wildme.wildbook_lite.bulkimport;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResult;

import jakarta.validation.Valid;

/**
 * Bulk-import surface.
 *
 *   POST /api/imports/encounters
 *
 * The frontend parses the xlsx (using sheetjs/xlsx in the browser) and
 * sends a JSON BulkImportRequest. The endpoint returns synchronously
 * with a per-row outcome list — caller can render exactly which rows
 * landed and which failed and why.
 *
 * Why we don't return 207 Multi-Status:
 *   - HTTP 207 carries one status per child entity which would be
 *     natural here, but it's a WebDAV-era convention that adds little
 *     in REST and confuses many client libraries. A 200 OK with a
 *     structured body works just as well in practice and is what
 *     real Wildbook returns.
 *
 * Future: switch to async (ImportJob entity, 202 + poll) if/when the
 * 1000-row cap becomes the bottleneck.
 */
@RestController
@RequestMapping("/api/imports")
public class BulkImportController {

    private final BulkImportService service;

    public BulkImportController(BulkImportService service) {
        this.service = service;
    }

    @PostMapping("/encounters")
    public BulkImportResult importEncounters(@Valid @RequestBody BulkImportRequest req) {
        return service.importBatch(req);
    }
}
