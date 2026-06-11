package com.wildme.wildbook_lite.search.opensearch;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.opensearch.core.BulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.repository.EncounterRepository;

/**
 * Full rebuild of the OpenSearch index from the DB.
 *
 * When you need this:
 *   - first time turning OS on (the index is empty; rows were created
 *     before the listener existed)
 *   - mapping change → you can't update most field types in place;
 *     reindex into a fresh index, then atomic-alias-swap (we cut that
 *     corner and just delete-and-recreate here)
 *   - after a partial outage where some events were dropped
 *
 * Pages through the DB in batches of N and bulk-sends each page to OS.
 * Keeps memory flat regardless of total row count.
 *
 * @Transactional(readOnly=true) so the JPA session keeps a single
 * connection for the whole walk and lazy-load fields work; readOnly
 * tells the DB we don't intend to mutate anything.
 */
@Service
@ConditionalOnProperty(value = "app.opensearch.enabled", havingValue = "true")
public class EncounterReindexService {

    private static final Logger log = LoggerFactory.getLogger(EncounterReindexService.class);
    private static final int BATCH = 500;

    private final EncounterRepository encRepo;
    private final EncounterIndexer indexer;

    public EncounterReindexService(EncounterRepository encRepo, EncounterIndexer indexer) {
        this.encRepo = encRepo;
        this.indexer = indexer;
    }

    @Audited("opensearch.reindex")
    @Transactional(readOnly = true)
    public ReindexReport reindexAll() throws IOException {
        indexer.recreateIndex();   // wipe + fresh mapping

        long total = 0;
        long ok = 0;
        long failed = 0;
        int page = 0;

        while (true) {
            org.springframework.data.domain.Page<Encounter> chunk =
                encRepo.findAll(PageRequest.of(page, BATCH));
            if (chunk.isEmpty()) break;

            List<EncounterIndexDocument> docs = chunk.getContent().stream()
                .map(EncounterIndexDocument::from)
                .toList();

            BulkResponse resp = indexer.bulkIndex(docs);
            for (var item : resp.items()) {
                total++;
                if (item.error() == null) ok++;
                else                       failed++;
            }
            log.info("[reindex] batch={} size={} ok-so-far={} failed-so-far={}",
                page, chunk.getNumberOfElements(), ok, failed);

            if (!chunk.hasNext()) break;
            page++;
        }
        return new ReindexReport(total, ok, failed);
    }

    public record ReindexReport(long total, long succeeded, long failed) {}
}
