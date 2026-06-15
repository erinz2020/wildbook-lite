package com.wildme.wildbook_lite.dto;

import java.time.LocalDateTime;

import com.wildme.wildbook_lite.encounter.LivingStatus;

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
 *
 * Richer biological fields (added with the taxonomy refactor):
 *   - taxonomyId: link to the species catalogue. When set, the service
 *     ALSO updates the denormalized `species` string to keep the fast
 *     filter in sync.
 *   - lifeStage / behavior / livingStatus: biological context.
 *   - locationId: hierarchical location path ("USA/CA/Monterey Bay").
 *   - decimalLatitude/Longitude: WGS84 GPS.
 *   - dynamicProperties: JSON string for site-specific custom fields.
 */
public record UpdateEncounterRequest(
    @Size(max = 255) String location,
    @Size(max = 64)  String species,
    LocalDateTime    encounterDate,
    @Size(max = 2000) String notes,
    Long individualId,
    Long observerId,

    Long taxonomyId,
    @Size(max = 255) String locationId,
    Double decimalLatitude,
    Double decimalLongitude,
    @Size(max = 32) String lifeStage,
    @Size(max = 5000) String behavior,
    LivingStatus livingStatus,
    @Size(max = 10000) String dynamicProperties
) {}
