package com.wildme.wildbook_lite.annotation.dto;

import com.wildme.wildbook_lite.annotation.Feature;
import com.wildme.wildbook_lite.annotation.FeatureType;

/**
 * Lightweight projection of Feature for API responses.
 * No annotation back-pointer — the caller already knows which
 * Annotation it asked for.
 */
public record FeatureResponse(
    Long id,
    FeatureType type,
    Long mediaAssetId
) {
    public static FeatureResponse from(Feature f) {
        return new FeatureResponse(
            f.getId(),
            f.getType(),
            f.getMediaAsset() == null ? null : f.getMediaAsset().getId()
        );
    }
}
