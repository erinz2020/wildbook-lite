package com.wildme.wildbook_lite.bulkimport.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/imports/encounters payload.
 *
 * Why a cap of 1000 rows:
 *   - Each row turns into a REQUIRES_NEW sub-transaction. 1000 short
 *     transactions per request is already aggressive — at the cap we
 *     spend ~1s of DB roundtrips just opening/closing them.
 *   - Larger imports should be chunked client-side and submitted as
 *     multiple batches. A future async ImportJob can lift this limit
 *     by removing the synchronous response-size pressure.
 *   - Hard ceiling prevents trivial abuse (someone POSTing a 10M-row
 *     JSON to exhaust memory).
 *
 * Auto-create flags are explicit so the caller knows up-front whether
 * their typo will create a phantom Taxonomy/Observer row.
 */
public record BulkImportRequest(

    @NotNull(message = "projectId is required")
    Long projectId,

    @NotEmpty(message = "rows must not be empty")
    @Size(max = 1000, message = "max 1000 rows per import; chunk client-side for bigger sets")
    List<BulkEncounterRow> rows,

    /** When true, missing scientificName values create new Taxonomy rows. Defaults to true. */
    Boolean autoCreateTaxonomy,

    /** When true, missing observerName values create new Observer rows. Defaults to true. */
    Boolean autoCreateObserver
) {

    /** Caller-friendly defaults via canonical ctor. */
    public BulkImportRequest {
        if (autoCreateTaxonomy == null) autoCreateTaxonomy = true;
        if (autoCreateObserver == null) autoCreateObserver = true;
    }
}
