package com.wildme.wildbook_lite.annotation.dto;

import com.wildme.wildbook_lite.annotation.Viewpoint;

/**
 * PATCH /api/annotations/{id}. Null = leave unchanged.
 *
 * We don't allow re-pointing the annotation to a different Encounter
 * or MediaAsset through PATCH — those are big semantic moves with their
 * own endpoints (TBD). Only field-level refinement here.
 */
public record UpdateAnnotationRequest(
    Double x,
    Double y,
    Double width,
    Double height,
    Double theta,
    String species,
    Viewpoint viewpoint,
    Double quality,
    Boolean exemplar
) {}
