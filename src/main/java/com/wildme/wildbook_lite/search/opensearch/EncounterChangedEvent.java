package com.wildme.wildbook_lite.search.opensearch;

/**
 * "Some write happened to this encounter — derived indexes should
 * resync." Fired by EncounterService for every mutation (create,
 * update, transition, assign, delete).
 *
 * Deliberately a separate event stream from EncounterCreatedEvent /
 * EncounterPublishedEvent (which drive notifications). Two listener
 * audiences, two concerns, two events — keep them decoupled so we
 * don't end up with "the indexer also has to know about notification
 * rules" type tangles.
 */
public record EncounterChangedEvent(
    Long encounterId,
    Kind kind
) {
    public enum Kind { UPSERT, DELETE }
}
