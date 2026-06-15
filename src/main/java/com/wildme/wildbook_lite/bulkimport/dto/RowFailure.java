package com.wildme.wildbook_lite.bulkimport.dto;

/**
 * Per-row failure report.
 *
 * `errorCode` is a stable machine-readable token so the UI can render
 * specific affordances (e.g., a "create individual" link next to
 * INDIVIDUAL_NOT_FOUND). `errorMessage` is for humans.
 */
public record RowFailure(
    Integer rowIndex,
    String errorCode,
    String errorMessage
) {

    public static final String CODE_VALIDATION         = "VALIDATION";
    public static final String CODE_INDIVIDUAL_MISSING = "INDIVIDUAL_NOT_FOUND";
    public static final String CODE_SPECIES_MISMATCH   = "SPECIES_MISMATCH";
    public static final String CODE_TAXONOMY_MISSING   = "TAXONOMY_NOT_FOUND";
    public static final String CODE_INTERNAL           = "INTERNAL";
}
