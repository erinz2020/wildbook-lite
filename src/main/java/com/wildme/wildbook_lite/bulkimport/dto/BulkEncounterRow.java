package com.wildme.wildbook_lite.bulkimport.dto;

import java.time.LocalDateTime;

import com.wildme.wildbook_lite.encounter.LivingStatus;

/**
 * One row in a bulk import. Mirrors the column set the frontend's xlsx
 * parser emits.
 *
 * Two "species" inputs:
 *   - `species` — denormalized name. Always written to encounter.species
 *     (kept for fast list filters). If only this is provided, no
 *     Taxonomy lookup happens.
 *   - `scientificName` — when present, the service looks up the
 *     Taxonomy row (creating one if `autoCreateTaxonomy=true`) and
 *     also overwrites `species` with the canonical name to keep
 *     denorm + relation in sync.
 *
 * `individualNickname` is a HARD lookup — we never auto-create
 * MarkedIndividuals from a bulk import (real Wildbook policy too;
 * registering an animal is a deliberate, supervised act).
 *
 * `observerName` IS find-or-create when autoCreateObserver=true.
 * Field researchers come and go; we want the path-of-least-friction
 * for a survey-leader uploading rows for an entire team.
 *
 * No projectId here — that lives on the parent BulkImportRequest;
 * cross-project rows in one batch would only add confusion.
 */
public record BulkEncounterRow(

    /** Optional client-supplied row index for traceability in the response. */
    Integer rowIndex,

    /** Free-text species name. At least one of species or scientificName is required. */
    String species,

    /** Canonical binomial — triggers Taxonomy lookup / create. */
    String scientificName,

    /** Optional, used only when auto-creating a new Taxonomy row. */
    String commonName,

    LocalDateTime encounterDate,

    String location,
    String locationId,
    Double decimalLatitude,
    Double decimalLongitude,

    String notes,
    String lifeStage,
    String behavior,
    LivingStatus livingStatus,
    String dynamicProperties,

    /** Existing Individual by nickname — fails the row if unknown. */
    String individualNickname,

    /** Field researcher name — find-or-create when autoCreateObserver=true. */
    String observerName,
    String observerEmail,
    String observerOrganization
) {}
