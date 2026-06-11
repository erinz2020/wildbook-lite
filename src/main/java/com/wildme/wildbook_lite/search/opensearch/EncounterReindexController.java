package com.wildme.wildbook_lite.search.opensearch;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin operations on the OpenSearch index.
 *
 * Gated by ADMIN at the URL via @PreAuthorize. The endpoint is
 * synchronous — for a real big-data deploy you'd kick this off as a
 * background job and return 202 Accepted + a job id to poll. For our
 * project scale the simple call is fine.
 */
@RestController
@RequestMapping("/api/admin/opensearch")
@ConditionalOnProperty(value = "app.opensearch.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class EncounterReindexController {

    private final EncounterReindexService reindex;

    public EncounterReindexController(EncounterReindexService reindex) {
        this.reindex = reindex;
    }

    @PostMapping("/reindex")
    public EncounterReindexService.ReindexReport reindex() throws IOException {
        return reindex.reindexAll();
    }
}
