package com.wildme.wildbook_lite.search.opensearch.dto;

import com.wildme.wildbook_lite.search.opensearch.EncounterIndexDocument;

public record OsHit(
    double score,
    EncounterIndexDocument document
) {}
