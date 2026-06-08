package com.wildme.wildbook_lite.controller;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.wildme.wildbook_lite.common.CsvWriter;
import com.wildme.wildbook_lite.service.EncounterService;

/**
 * Streaming CSV export. The response is sent row-by-row; memory stays flat
 * even at 10M rows.
 *
 * GET /api/encounters/export.csv?projectId=42
 *
 * Interview points:
 *
 *  - Why StreamingResponseBody, not a String return: the latter buffers the
 *    whole body in memory before flushing → OOM at scale.
 *  - Backpressure: TCP write back-pressures the OS buffer, the
 *    OutputStreamWriter blocks, our forEach pauses, JPA cursor pauses. One
 *    chain, naturally synchronized.
 *  - For *very large* exports (>1M rows) most prod systems shift to async
 *    jobs: enqueue, process to S3, email the user a signed URL. We're
 *    keeping it synchronous here because the project scale doesn't justify
 *    the extra infrastructure.
 */
@RestController
@RequestMapping("/api/encounters")
public class EncounterExportController {

    private final EncounterService encounterService;

    public EncounterExportController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(@RequestParam Long projectId) {

        StreamingResponseBody body = out -> {
            // Wrap the raw OutputStream in a writer; UTF-8 + autoflush off,
            // we control flush points to avoid syscalls per row.
            PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), false);
            try {
                // Excel-friendly UTF-8 BOM so Mac/Windows Excel both render Chinese correctly.
                w.write('﻿');
                w.println(CsvWriter.row("id", "projectId", "species", "location", "encounterDate", "notes"));

                encounterService.streamByProject(projectId, e -> {
                    w.println(CsvWriter.row(
                        e.getId(),
                        e.getProjectId(),
                        e.getSpecies(),
                        e.getLocation(),
                        e.getEncounterDate(),
                        e.getNotes()
                    ));
                });
            } finally {
                w.flush();
            }
        };

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"encounters-project-" + projectId + ".csv\"")
            .body(body);
    }
}
