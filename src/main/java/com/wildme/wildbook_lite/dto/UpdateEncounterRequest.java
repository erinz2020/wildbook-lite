package com.wildme.wildbook_lite.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Size;

/**
 * Partial-update payload for an Encounter.
 *
 * Convention:
 *   - null = leave the field unchanged (PATCH semantics)
 *   - non-null = overwrite
 *
 * What you CANNOT change here:
 *   - id, projectId, submitterUserId, status, version, createdAt — these
 *     are either identity or workflow concerns with dedicated endpoints
 *     (e.g., POST /transition for status, POST /assign for assignee).
 *     Letting everything through one PATCH is the classic way to leak
 *     authorization rules.
 *   - To unlink Individual/Observer (set to null), use the dedicated
 *     PATCH /individual endpoint or assign endpoints; PATCH here only
 *     *sets* a relation, not clears it.
 */
public record UpdateEncounterRequest(
    @Size(max = 255) String location,
    @Size(max = 64)  String species,
    LocalDateTime    encounterDate,
    @Size(max = 2000) String notes,
    Long individualId,
    Long observerId
) {}
