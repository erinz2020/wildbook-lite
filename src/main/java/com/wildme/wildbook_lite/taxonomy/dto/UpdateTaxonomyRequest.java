package com.wildme.wildbook_lite.taxonomy.dto;

import jakarta.validation.constraints.Size;

public record UpdateTaxonomyRequest(
    @Size(max = 128) String scientificName,
    @Size(max = 64) String genus,
    @Size(max = 64) String specificEpithet,
    @Size(max = 512) String commonNames,
    Long itisTsn
) {}
