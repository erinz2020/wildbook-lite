package com.wildme.wildbook_lite.search.opensearch.dto;

import java.util.List;

public record OsSearchResponse(
    long totalHits,
    int from,
    int size,
    List<OsHit> hits
) {}
