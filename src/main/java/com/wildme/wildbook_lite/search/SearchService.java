package com.wildme.wildbook_lite.search;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.search.dto.SearchHit;
import com.wildme.wildbook_lite.search.dto.SearchResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Postgres full-text search across Encounter.species / location / notes.
 *
 * Why Postgres FTS (not pg_trgm / not Elasticsearch):
 *
 *  - FTS gives lexer (tokenization), stemming ("swimming" matches "swim"),
 *    and stop-word handling out of the box via to_tsvector / to_tsquery.
 *  - pg_trgm is for fuzzy *substring* matches (typo tolerance) and is a
 *    great complement, not a replacement.
 *  - Elasticsearch is "real" search at scale, but doubles the ops surface.
 *    For ≤10M docs FTS is usually plenty.
 *
 * Performance note (interview):
 *  - Without an index, Postgres recomputes to_tsvector(...) for every row
 *    every query → sequential scan. Production: a GENERATED ALWAYS column
 *    plus a GIN index on it, e.g.:
 *
 *      ALTER TABLE encounter ADD COLUMN search_vec tsvector
 *        GENERATED ALWAYS AS (
 *          setweight(to_tsvector('english', coalesce(species,'')),  'A') ||
 *          setweight(to_tsvector('english', coalesce(location,'')), 'B') ||
 *          setweight(to_tsvector('english', coalesce(notes,'')),    'C')
 *        ) STORED;
 *      CREATE INDEX ix_encounter_search ON encounter USING GIN (search_vec);
 *
 *    Then the WHERE becomes `WHERE search_vec @@ websearch_to_tsquery('english', :q)`.
 *
 *  - We are intentionally on the simple (slow on big data) variant here
 *    because creating GIN indexes requires a Flyway migration, which is
 *    on the roadmap but not yet enabled.
 */
@Service
public class SearchService {

    private final ProjectGuard projectGuard;

    @PersistenceContext
    private EntityManager em;

    public SearchService(ProjectGuard projectGuard) {
        this.projectGuard = projectGuard;
    }

    @Transactional(readOnly = true)
    public SearchResponse searchEncounters(Long projectId, String q, int limit) {
        if (q == null || q.isBlank()) {
            return new SearchResponse(q, 0, List.of());
        }
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }
        int cappedLimit = Math.min(Math.max(limit, 1), 100);

        String sql = """
            SELECT e.id, e.project_id, e.species, e.location, e.notes,
                   ts_rank(
                     to_tsvector('english',
                         coalesce(e.species,'')  || ' ' ||
                         coalesce(e.location,'') || ' ' ||
                         coalesce(e.notes,'')),
                     websearch_to_tsquery('english', :q)
                   ) AS score
              FROM encounter e
             WHERE e.project_id = :projectId
               AND to_tsvector('english',
                       coalesce(e.species,'')  || ' ' ||
                       coalesce(e.location,'') || ' ' ||
                       coalesce(e.notes,''))
                   @@ websearch_to_tsquery('english', :q)
             ORDER BY score DESC, e.id DESC
             LIMIT :limit
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
            .setParameter("q", q)
            .setParameter("projectId", projectId)
            .setParameter("limit", cappedLimit)
            .getResultList();

        List<SearchHit> hits = rows.stream()
            .map(r -> new SearchHit(
                ((Number) r[0]).longValue(),
                r[1] == null ? null : ((Number) r[1]).longValue(),
                (String) r[2],
                (String) r[3],
                (String) r[4],
                ((Number) r[5]).doubleValue()
            ))
            .toList();
        return new SearchResponse(q, hits.size(), hits);
    }
}
