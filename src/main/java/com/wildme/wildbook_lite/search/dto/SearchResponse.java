package com.wildme.wildbook_lite.search.dto;

import java.util.List;

public record SearchResponse(
    String query,
    int totalHits,
    List<SearchHit> hits
) {}
