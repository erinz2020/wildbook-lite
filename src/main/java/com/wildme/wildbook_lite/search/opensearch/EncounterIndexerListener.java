package com.wildme.wildbook_lite.search.opensearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wildme.wildbook_lite.repository.EncounterRepository;

/**
 * Async listener that keeps the OpenSearch index eventually-consistent
 * with the DB. Three properties that matter:
 *
 *   - @TransactionalEventListener(phase = AFTER_COMMIT)
 *       We only reindex AFTER the originating DB transaction commits.
 *       If the encounter write rolls back, we MUST NOT have pushed a
 *       phantom doc into the index. This is the classic "write to two
 *       systems" pitfall and AFTER_COMMIT is the standard fix.
 *
 *   - @Async
 *       Runs on applicationTaskExecutor, not the request thread. A
 *       slow / down OpenSearch never slows down the user's HTTP response.
 *       The DB is the source of truth — if OS is behind, search results
 *       are stale, but writes still succeed.
 *
 *   - We re-read the encounter from the DB inside the listener instead
 *       of trusting an in-memory snapshot in the event. This makes the
 *       indexer race-safe: if multiple updates fire in sequence, every
 *       listener invocation indexes the LATEST committed state, which
 *       is what we want.
 *
 * Failure mode: if OpenSearch is offline, we log and swallow. A real
 * production system would push the failed id into a "needs reindex"
 * outbox table and have a scheduled job retry. We don't have that
 * outbox yet — see the roadmap.
 */
@Component
@ConditionalOnProperty(value = "app.opensearch.enabled", havingValue = "true")
public class EncounterIndexerListener {

    private static final Logger log = LoggerFactory.getLogger(EncounterIndexerListener.class);

    private final EncounterIndexer indexer;
    private final EncounterRepository encRepo;

    public EncounterIndexerListener(EncounterIndexer indexer, EncounterRepository encRepo) {
        this.indexer = indexer;
        this.encRepo = encRepo;
    }

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChange(EncounterChangedEvent event) {
        try {
            if (event.kind() == EncounterChangedEvent.Kind.DELETE) {
                indexer.deleteById(event.encounterId());
                log.debug("[opensearch] deleted doc id={}", event.encounterId());
                return;
            }
            // UPSERT: re-read latest state and push.
            encRepo.findById(event.encounterId())
                .ifPresent(e -> {
                    try {
                        indexer.index(EncounterIndexDocument.from(e));
                        log.debug("[opensearch] indexed doc id={}", e.getId());
                    } catch (Exception ex) {
                        log.warn("[opensearch] index failed id={} cause={}",
                            e.getId(), ex.toString());
                    }
                });
        } catch (Exception ex) {
            log.warn("[opensearch] listener failed event={} cause={}", event, ex.toString());
        }
    }
}
