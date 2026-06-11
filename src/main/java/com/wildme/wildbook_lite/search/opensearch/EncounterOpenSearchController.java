package com.wildme.wildbook_lite.search.opensearch;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.search.opensearch.dto.OsSearchResponse;

/**
 * GET /api/search/opensearch/encounters
 *
 * Sits alongside the existing /api/search/encounters (Postgres FTS).
 * Same shape of query, different backend — handy for benchmarking
 * the two and explaining the tradeoffs.
 *
 *   ?projectId=1                       (required, permission-gated)
 *   ?q=humpback                        (optional, free text)
 *   ?status=PUBLISHED                  (optional, filter)
 *   ?from=0&size=20                    (OS pagination uses from/size,
 *                                      NOT Spring Pageable)
 */
@RestController
@RequestMapping("/api/search/opensearch")
@ConditionalOnProperty(value = "app.opensearch.enabled", havingValue = "true")
public class EncounterOpenSearchController {

    private final EncounterOpenSearchService service;

    public EncounterOpenSearchController(EncounterOpenSearchService service) {
        this.service = service;
    }

    @GetMapping("/encounters")
    public OsSearchResponse search(
            @RequestParam Long projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EncounterStatus status,
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "20") int size) throws IOException {
        return service.search(projectId, q, status, from, size);
    }
}
