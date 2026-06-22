package com.wildme.wildbook_lite.bulkimport;

/**
 * Lifecycle status of a bulk import task.
 *
 * Legal transitions (enforced in the service for now; no explicit
 * state-machine table yet — 6 states is small enough to not need one):
 *
 *   PENDING → IMPORTING → IMPORTED → PROCESSING_IA → COMPLETED
 *                  ↘ FAILED ↙
 *
 * 
 * Add a Transition table (like EncounterStatus) only if validation
 * of transitions becomes necessary.
 */
public enum BulkImportStatus {
    PENDING,
    IMPORTING,
    IMPORTED,
    PROCESSING_IA,
    COMPLETED,
    FAILED
}