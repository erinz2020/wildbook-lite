package com.wildme.wildbook_lite.occurrence.dto;

import java.time.LocalDateTime;

import com.wildme.wildbook_lite.occurrence.Platform;

import jakarta.validation.constraints.NotNull;

/**
 * Payload for POST /api/occurrences.
 *
 * Only projectId is strictly required — everything else can be added/
 * refined later via PATCH. dateTime is highly recommended (otherwise
 * the row is hard to place on a timeline) so we recommend the client
 * supply it but don't enforce, mirroring real-world field-data entry.
 */
public record CreateOccurrenceRequest(
    @NotNull Long projectId,
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
    String comments
) {}
