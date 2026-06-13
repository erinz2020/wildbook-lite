package com.wildme.wildbook_lite.occurrence.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.occurrence.Occurrence;
import com.wildme.wildbook_lite.occurrence.Platform;

/**
 * Response envelope for GET /api/occurrences/{id}.
 *
 * We deliberately don't return the raw Occurrence entity for the detail
 * endpoint — we want to attach derived fields (encounterCount, the
 * list of distinct species) without forcing those onto the entity.
 *
 * The list endpoint still returns Page<Occurrence> directly because the
 * derived counts would N+1 across the page; consumers needing the
 * summary should request the detail endpoint per row.
 */
public record OccurrenceResponse(
    Long id,
    Long projectId,
    LocalDateTime dateTime,
    String location,
    Double decimalLatitude,
    Double decimalLongitude,
    String weather,
    Platform platform,
    String transect,
    Integer groupSizeMin,
    Integer groupSizeMax,
    Integer numAdults,
    Integer numJuveniles,
    Integer numCalves,
    String comments,
    Long submitterUserId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int encounterCount,
    List<String> species,
    List<Long> encounterIds
) {

    public static OccurrenceResponse from(Occurrence o) {
        List<Encounter> encs = o.getEncounters();
        List<String> speciesList = encs == null ? List.of()
            : encs.stream()
                .map(Encounter::getSpecies)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        List<Long> encIds = encs == null ? List.of()
            : encs.stream().map(Encounter::getId).sorted().collect(Collectors.toList());

        return new OccurrenceResponse(
            o.getId(),
            o.getProjectId(),
            o.getDateTime(),
            o.getLocation(),
            o.getDecimalLatitude(),
            o.getDecimalLongitude(),
            o.getWeather(),
            o.getPlatform(),
            o.getTransect(),
            o.getGroupSizeMin(),
            o.getGroupSizeMax(),
            o.getNumAdults(),
            o.getNumJuveniles(),
            o.getNumCalves(),
            o.getComments(),
            o.getSubmitterUserId(),
            o.getCreatedAt(),
            o.getUpdatedAt(),
            encs == null ? 0 : encs.size(),
            speciesList,
            encIds
        );
    }
}
