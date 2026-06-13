package com.wildme.wildbook_lite.occurrence.dto;

import java.time.LocalDateTime;

import com.wildme.wildbook_lite.occurrence.Platform;

/**
 * PATCH payload — every field nullable. Service applies "only if not
 * null" semantics. Note we deliberately do NOT allow re-pointing
 * projectId (would break the project-isolation invariant). To "move"
 * an occurrence to a new project, delete + recreate.
 */
public record UpdateOccurrenceRequest(
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
