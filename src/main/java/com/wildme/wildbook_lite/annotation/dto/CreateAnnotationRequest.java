package com.wildme.wildbook_lite.annotation.dto;

import com.wildme.wildbook_lite.annotation.Viewpoint;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/encounters/{id}/annotations payload.
 *
 * Two flavours of annotation:
 *
 *  1. BBOX annotation (the normal case): all of x, y, width, height are
 *     required; theta is optional (default 0). mediaAssetId points at
 *     the image. The service creates the Annotation row + one BBOX
 *     Feature linking it to that MediaAsset.
 *
 *  2. TRIVIAL annotation: when you want to mark "this whole image
 *     contains the animal" without drawing a box. Pass mediaAssetId
 *     and leave the bbox fields null; the service creates the
 *     Annotation row + one TRIVIAL Feature.
 *
 * Service validates the bbox-vs-trivial coherence (you can't pass
 * partial bbox params; either all or none).
 */
public record CreateAnnotationRequest(
    @NotNull Long mediaAssetId,
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
