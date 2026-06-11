package com.wildme.wildbook_lite.search.opensearch;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.config.AppProperties;
import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.search.opensearch.dto.OsHit;
import com.wildme.wildbook_lite.search.opensearch.dto.OsSearchResponse;

/**
 * Read side. Builds a bool-query against the encounters index.
 *
 * Query shape (interview-quality cheat sheet):
 *
 *   must     — all of these must match (combined with AND, scored)
 *   filter   — like must, but NOT scored (faster, used for filters
 *              that don't affect relevance: projectId, status, ranges)
 *   should   — at least one should match (OR-ish, contributes to score)
 *   must_not — none of these may match (NOT)
 *
 * For our case:
 *   - free-text q hits species + location + notes
 *     ⇒ multi_match in `must` (scored — we want the best match on top)
 *   - projectId, status, etc. ⇒ `filter` (binary yes/no, no score impact)
 */
@Service
@ConditionalOnProperty(value = "app.opensearch.enabled", havingValue = "true")
public class EncounterOpenSearchService {

    private final OpenSearchClient client;
    private final String indexName;
    private final ProjectGuard projectGuard;

    public EncounterOpenSearchService(OpenSearchClient client,
                                      AppProperties props,
                                      ProjectGuard projectGuard) {
        this.client = client;
        this.indexName = props.opensearch().indexName();
        this.projectGuard = projectGuard;
    }

    public OsSearchResponse search(Long projectId,
                                   String q,
                                   EncounterStatus status,
                                   int from,
                                   int size) throws IOException {
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }
        int safeFrom = Math.max(from, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        // bool query: must = free text; filter = project + status
        Query.Builder queryBuilder = new Query.Builder();
        queryBuilder.bool(b -> {
            b.filter(f -> f.term(t -> t
                .field("projectId")
                .value(FieldValue.of(projectId))));
            if (status != null) {
                b.filter(f -> f.term(t -> t
                    .field("status")
                    .value(FieldValue.of(status.name()))));
            }
            if (q != null && !q.isBlank()) {
                b.must(m -> m.multiMatch(mm -> mm
                    .query(q)
                    .fields("species^3", "location^2", "notes")
                    // ^3 / ^2 = field boost: a hit in species is worth
                    // more than one in notes.
                ));
            } else {
                // No text → return everything in this project; OS still
                // wants a clause in `must`, so use match_all.
                b.must(m -> m.matchAll(ma -> ma));
            }
            return b;
        });

        SearchResponse<EncounterIndexDocument> resp = client.search(s -> s
            .index(indexName)
            .query(queryBuilder.build())
            .from(safeFrom)
            .size(safeSize)
            .sort(so -> so.field(f -> f.field("_score").order(SortOrder.Desc))),
            EncounterIndexDocument.class);

        List<OsHit> hits = resp.hits().hits().stream()
            .map(EncounterOpenSearchService::toHit)
            .toList();

        long total = resp.hits().total() != null ? resp.hits().total().value() : hits.size();
        return new OsSearchResponse(total, safeFrom, safeSize, hits);
    }

    private static OsHit toHit(Hit<EncounterIndexDocument> h) {
        EncounterIndexDocument d = h.source();
        Double score = h.score();
        return new OsHit(score == null ? 0.0 : score, d);
    }
}
