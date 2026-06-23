package com.wildme.wildbook_lite.bulkimport;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResponse;

import java.util.UUID;


@RestController
@RequestMapping("/api/bulk-import")

public class BulkImportController {


    private final BulkImportService service;

    public BulkImportController(BulkImportService service) {
        this.service = service;
    } 

    @PostMapping
    public ResponseEntity<BulkImportResponse> create (@Valid @RequestBody BulkImportRequest request) {
        BulkImportResponse resp = service.submit(request);
        return ResponseEntity.accepted().body(resp);

    }

    @GetMapping("/{bulkImportId}")
    public ResponseEntity<BulkImportResponse> get(@PathVariable UUID bulkImportId) {
        return ResponseEntity.ok(service.getStatus(bulkImportId));
    }
}