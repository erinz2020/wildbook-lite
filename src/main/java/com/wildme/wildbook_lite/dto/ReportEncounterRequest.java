package com.wildme.wildbook_lite.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.wildme.wildbook_lite.common.ValidSpecies;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Higher-level "I want to file an encounter report" payload.
 *
 * Difference from CreateEncounterRequest:
 *   - bundles linkage to Individual / Observer
 *   - bundles a list of existing Sighting ids that the submitter wants
 *     to consolidate under this encounter
 *   - submitter is captured from the auth principal — NOT taken from
 *     the body, because we don't want a client to claim someone else
 *     filed the report
 *
 * Many fields are optional: the typical real-world flow lets a
 * researcher submit a partial draft and refine later (DRAFT status).
 */
public record ReportEncounterRequest(

    @NotNull(message = "projectId is required")
    Long projectId,

    /** Species name — uses the custom validator so it can't be empty/garbage. */
    @ValidSpecies
    String species,

    @Size(max = 255)
    String location,

    LocalDateTime encounterDate,

    @Size(max = 2000)
    String notes,

    /** Optional: link to an already-tracked animal. Species must match. */
    Long individualId,

    /** Optional: the field researcher who actually saw the animal. */
    Long observerId,

    /**
     * Optional: existing orphan Sightings to attach to this Encounter.
     * Any sighting that already belongs to a different Encounter is
     * rejected at the service layer (no silent re-parenting).
     */
    @Size(max = 50, message = "max 50 sightings per report")
    List<Long> sightingIds,

    /**
     * Optional: attach the new Encounter to an existing Occurrence
     * (survey group event). The Occurrence must belong to the same
     * project; service rejects mismatches.
     */
    Long occurrenceId
) {}
