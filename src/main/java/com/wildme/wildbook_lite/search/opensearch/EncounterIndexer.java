package com.wildme.wildbook_lite.search.opensearch;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wildme.wildbook_lite.config.AppProperties;

/**
 * Low-level write side. Knows the index name, the mapping, and how to
 * push one doc or bulk-push many.
 *
 * Mapping decisions:
 *   - id / *Id fields → keyword (exact match, no analysis).
 *   - species, location → text (analyzed) + a `.raw` keyword sub-field
 *     for exact filtering and aggregations. This dual-mapping idiom
 *     is the most common gotcha for newcomers: "why does my term query
 *     on `species` return nothing? oh right, the value got tokenized."
 *   - notes → text (free-form, analyzed).
 *   - status, encounterDate → keyword / date.
 *
 * We don't tune analyzers per language here. In real Wildbook you'd
 * pick a stemmer per locale or use ICU.
 */
@Component
@ConditionalOnProperty(value = "app.opensearch.enabled", havingValue = "true")
public class EncounterIndexer {

    private static final Logger log = LoggerFactory.getLogger(EncounterIndexer.class);

    private final OpenSearchClient client;
    private final String indexName;

    public EncounterIndexer(OpenSearchClient client, AppProperties props) {
        this.client = client;
        this.indexName = props.opensearch().indexName();
    }

    /**
     * Try once on startup. Failure here is logged-and-swallowed:
     * OpenSearch being down should never crash the app — search just
     * won't work until OS comes back. The lazy transport will retry
     * on the next request.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initOnReady() {
        try {
            ensureIndex();
            log.info("[opensearch] index '{}' ready", indexName);
        } catch (Exception e) {
            log.warn("[opensearch] could not init index '{}' on startup: {}",
                indexName, e.toString());
        }
    }

    /** Idempotent: creates the index with our mapping if it doesn't already exist. */
    public void ensureIndex() throws IOException {
        boolean exists = client.indices()
            .exists(ExistsRequest.of(b -> b.index(indexName)))
            .value();
        if (exists) return;

        CreateIndexRequest req = CreateIndexRequest.of(b -> b
            .index(indexName)
            .settings(IndexSettings.of(s -> s
                .numberOfShards("1")
                .numberOfReplicas("0"))) // dev: single node, no replicas
            .mappings(m -> m
                .properties("id",               Property.of(p -> p.long_(v -> v)))
                .properties("projectId",        Property.of(p -> p.long_(v -> v)))
                .properties("submitterUserId",  Property.of(p -> p.long_(v -> v)))
                .properties("assignedToUserId", Property.of(p -> p.long_(v -> v)))
                .properties("individualId",     Property.of(p -> p.long_(v -> v)))
                .properties("observerId",       Property.of(p -> p.long_(v -> v)))
                .properties("status",           Property.of(p -> p.keyword(k -> k)))
                .properties("species",          Property.of(p -> p.text(t -> t
                    .fields("raw", Property.of(rp -> rp.keyword(k -> k))))))
                .properties("location",         Property.of(p -> p.text(t -> t
                    .fields("raw", Property.of(rp -> rp.keyword(k -> k))))))
                .properties("notes",            Property.of(p -> p.text(t -> t)))
                .properties("encounterDate",    Property.of(p -> p.date(d -> d)))
                .properties("updatedAt",        Property.of(p -> p.date(d -> d))))
        );
        client.indices().create(req);
    }

    /** Upsert a single document. Same call shape covers create-vs-update. */
    public void index(EncounterIndexDocument doc) throws IOException {
        client.index(b -> b
            .index(indexName)
            .id(String.valueOf(doc.id()))
            .document(doc));
    }

    /**
     * Bulk-index for reindex. Bulk is dramatically faster than a loop
     * of single index() calls because it's one HTTP round-trip per batch.
     * For very large datasets we'd page through DB in chunks of N and
     * issue one bulk request per chunk.
     */
    public BulkResponse bulkIndex(List<EncounterIndexDocument> docs) throws IOException {
        BulkRequest.Builder br = new BulkRequest.Builder().index(indexName);
        for (EncounterIndexDocument doc : docs) {
            br.operations(BulkOperation.of(b -> b.index(
                IndexOperation.of(io -> io
                    .id(String.valueOf(doc.id()))
                    .document(doc)
                ))));
        }
        return client.bulk(br.build());
    }

    public void deleteById(Long id) throws IOException {
        try {
            client.delete(b -> b.index(indexName).id(String.valueOf(id)));
        } catch (OpenSearchException ex) {
            // 404 on delete is fine — already gone.
            if (ex.status() != 404) throw ex;
        }
    }

    /** Wipe and recreate. Used by the admin reindex flow. */
    public void recreateIndex() throws IOException {
        boolean exists = client.indices()
            .exists(ExistsRequest.of(b -> b.index(indexName)))
            .value();
        if (exists) {
            client.indices().delete(b -> b.index(indexName));
        }
        ensureIndex();
    }
}
