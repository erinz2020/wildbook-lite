package com.wildme.wildbook_lite.export;

import java.time.LocalDateTime;

import com.wildme.wildbook_lite.encounter.EncounterStatus;

/**
 * Optional filters applied to an Encounter export.
 *
 * Filters here are applied **in memory while iterating the stream**.
 * The DB still scans every row in the project but we throw rows away
 * before they become CSV bytes. Memory stays bounded at one row at a
 * time regardless of filter selectivity.
 *
 * Trade-off acknowledged: for a project with 10M rows and a filter
 * selecting 100, this returns 99.999% of rows just to discard them.
 * If/when that becomes a measured pain point, swap to a JPQL @Query
 * with optional WHERE clauses returning Stream<Encounter>. For the
 * scale we model today, the simpler shape is the right call.
 *
 * Null means "no filter on that field". All filters AND together.
 */
public record ExportFilters(
    String species,
    EncounterStatus status,
    LocalDateTime from,
    LocalDateTime to
) {

    /** Convenience "match everything" — used by tests + the default endpoint. */
    public static final ExportFilters NONE = new ExportFilters(null, null, null, null);

    /**
     * Applied row-by-row. Returns true when the encounter should be
     * included in the output.
     */
    public boolean accepts(com.wildme.wildbook_lite.entity.Encounter e) {
        if (species != null && !species.equalsIgnoreCase(e.getSpecies())) return false;
        if (status != null  && status != e.getStatus()) return false;
        if (from != null    && (e.getEncounterDate() == null || e.getEncounterDate().isBefore(from))) return false;
        if (to != null      && (e.getEncounterDate() == null || e.getEncounterDate().isAfter(to))) return false;
        return true;
    }
}
