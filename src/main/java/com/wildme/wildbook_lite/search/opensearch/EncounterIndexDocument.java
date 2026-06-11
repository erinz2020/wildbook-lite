package com.wildme.wildbook_lite.search.opensearch;

import java.time.Instant;
import java.time.LocalDateTime;

import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.entity.Encounter;

/**
 * The shape of an Encounter as it lives in OpenSearch.
 *
 * Why a separate type, not just serializing the Encounter entity:
 *
 *   - The DB row has fields we don't want in the search index (JPA
 *     relations, lazy collections, version column). Serializing the
 *     whole entity would also drag along Hibernate proxies and blow up.
 *   - The index is a *derived view*. Mapping types deliberately differ
 *     from the DB types: e.g., a text field for "notes" benefits from
 *     analysis (tokenizer + stop-words), while the DB stores a raw
 *     varchar. A separate POJO makes the contract with OS explicit.
 *   - When the schema evolves, we update the doc mapper here; entity
 *     code stays untouched.
 *
 * Indexed fields:
 *   id, projectId, status, submitterUserId, assignedToUserId,
 *   species, location, notes, encounterDate, individualId, observerId,
 *   createdAt, updatedAt (so admin can sort/filter "recent" results).
 */
public record EncounterIndexDocument(
    Long id,
    Long projectId,
    EncounterStatus status,
    Long submitterUserId,
    Long assignedToUserId,
    String species,
    String location,
    String notes,
    LocalDateTime encounterDate,
    Long individualId,
    Long observerId,
    Instant updatedAt
) {
    /** Snapshot the JPA entity into the search-shape. */
    public static EncounterIndexDocument from(Encounter e) {
        return new EncounterIndexDocument(
            e.getId(),
            e.getProjectId(),
            e.getStatus(),
            e.getSubmitterUserId(),
            e.getAssignedToUserId(),
            e.getSpecies(),
            e.getLocation(),
            e.getNotes(),
            e.getEncounterDate(),
            e.getIndividual() == null ? null : e.getIndividual().getId(),
            e.getObserver()   == null ? null : e.getObserver().getId(),
            Instant.now()
        );
    }
}
