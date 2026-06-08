package com.wildme.wildbook_lite.search.dto;

public record SearchHit(
    Long id,
    Long projectId,
    String species,
    String location,
    String notes,
    /** Postgres ts_rank score; higher is better. */
    double score
) {}
