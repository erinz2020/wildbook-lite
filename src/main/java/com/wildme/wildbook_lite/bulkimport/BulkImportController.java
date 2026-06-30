package com.wildme.wildbook_lite.bulkimport;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RestController
@RequestMapping("/api/bulk-import")
@RequiredArgsConstructor                                  // Lombok: constructor for the final `service` field
@Tag(name = "Bulk Import", description = "Asynchronous batch import of records")  // Swagger: groups these endpoints
public class BulkImportController {

    private final BulkImportService service;

    @Operation(                                           // Swagger: describes this endpoint
        summary = "Submit a bulk import",
        description = "Validates synchronously, then returns 202 with a task; the import runs in the background.")
    @ApiResponses({                                       // Swagger: documents the possible responses
        @ApiResponse(responseCode = "202", description = "Accepted; processing in the background"),
        @ApiResponse(responseCode = "400", description = "Table-level validation failed"),
        @ApiResponse(responseCode = "409", description = "Duplicate bulkImportId"),
        @ApiResponse(responseCode = "422", description = "Too many invalid rows")
    })
    @PostMapping
    public ResponseEntity<BulkImportResponse> create(@Valid @RequestBody BulkImportRequest request) {
        BulkImportResponse resp = service.submit(request);
        return ResponseEntity.accepted().body(resp);
    }

    @Operation(summary = "Poll import status", description = "Returns the task status and progress counters.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current status and progress"),
        @ApiResponse(responseCode = "404", description = "No task for that bulkImportId")
    })
    @GetMapping("/{bulkImportId}")
    public ResponseEntity<BulkImportResponse> get(
            @Parameter(description = "The client-generated idempotency key used at submit time")  // Swagger: documents the path param
            @PathVariable UUID bulkImportId) {
        return ResponseEntity.ok(service.getStatus(bulkImportId));
    }
}
