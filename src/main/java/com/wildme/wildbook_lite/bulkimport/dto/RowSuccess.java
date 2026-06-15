package com.wildme.wildbook_lite.bulkimport.dto;

/**
 * Per-row success report.
 *
 * The "wasCreated" flags tell the caller "we made a new reference row
 * for you" — important for surfacing typos. If a user spelled
 * `Megaptera novaeangliae` two different ways, autoCreateTaxonomy=true
 * would silently produce two species rows; surfacing the "created"
 * flag in the response lets the UI flag possible typos.
 */
public record RowSuccess(
    Integer rowIndex,
    Long encounterId,
    Long taxonomyId,
    boolean taxonomyCreated,
    Long observerId,
    boolean observerCreated,
    Long individualId
) {}
