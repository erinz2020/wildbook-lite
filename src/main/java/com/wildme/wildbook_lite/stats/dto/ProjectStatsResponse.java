package com.wildme.wildbook_lite.stats.dto;

import java.util.List;

public record ProjectStatsResponse(
    Long projectId,
    long totalEncounters,
    long totalMembers,
    long totalMedia,
    long totalComments,
    List<SpeciesCount> topSpecies
) {}
