package com.wildme.wildbook_lite.export;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.wildme.wildbook_lite.encounter.EncounterStatus;

/**
 * Streaming CSV export for a project's encounters.
 *
 *   GET /api/projects/{projectId}/encounters/export.csv
 *       ?species=...      (optional, exact match)
 *       &status=PUBLISHED (optional)
 *       &from=2026-01-01T00:00:00 (optional, encounterDate lower bound)
 *       &to=2026-12-31T23:59:59   (optional, encounterDate upper bound)
 *
 * Returns: text/csv; charset=UTF-8 as a download attachment.
 *
 * Why {@link StreamingResponseBody} (not just returning a String / byte[]):
 *  - Spring writes chunks directly to the response output stream from a
 *    worker thread. The response body never lands in memory as one blob.
 *  - The MVC layer keeps the request thread free during the entire
 *    export; only the worker is tied up. (For Tomcat: configurable via
 *    `spring.mvc.async.request-timeout`.)
 *  - Combined with the server-side JPA cursor in the service, this is
 *    OOM-proof for arbitrarily large projects.
 *
 * Why Content-Disposition: attachment with a generated filename:
 *  - Forces the browser to download instead of render (we serve text/csv
 *    which most browsers will happily display inline otherwise).
 *  - Filename embeds the project id + a UTC timestamp so multiple
 *    exports of the same project don't overwrite each other in the
 *    user's downloads folder.
 *
 * SecurityContext propagation: Spring Security automatically propagates
 * the security context to async dispatch threads, so the projectGuard
 * check inside the service still sees the caller's principal. We don't
 * need any manual context-copy plumbing.
 */
@RestController
public class EncounterExportController {

    private static final DateTimeFormatter FNAME_TS =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final EncounterExportService exportService;

    public EncounterExportController(EncounterExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping(value = "/api/projects/{projectId}/encounters/export.csv",
                produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @PathVariable Long projectId,
            @RequestParam(required = false) String species,
            @RequestParam(required = false) EncounterStatus status,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        ExportFilters filters = new ExportFilters(species, status, from, to);
        String filename = "encounters-project-" + projectId + "-"
            + FNAME_TS.format(LocalDateTime.now()) + ".csv";

        StreamingResponseBody body = out -> exportService.writeCsv(projectId, filters, out);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(body);
    }
}
