package com.wildme.wildbook_lite.annotation.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.wildme.wildbook_lite.annotation.Annotation;
import com.wildme.wildbook_lite.annotation.Viewpoint;

public record AnnotationResponse(
    Long id,
    Long encounterId,
    Double x,
    Double y,
    Double width,
    Double height,
    Double theta,
    String species,
    Viewpoint viewpoint,
    Double quality,
    boolean exemplar,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<FeatureResponse> features
) {

    public static AnnotationResponse from(Annotation a) {
        List<FeatureResponse> feats = a.getFeatures() == null ? List.of()
            : a.getFeatures().stream().map(FeatureResponse::from).collect(Collectors.toList());
        return new AnnotationResponse(
            a.getId(),
            a.getEncounter() == null ? null : a.getEncounter().getId(),
            a.getX(),
            a.getY(),
            a.getWidth(),
            a.getHeight(),
            a.getTheta(),
            a.getSpecies(),
            a.getViewpoint(),
            a.getQuality(),
            a.isExemplar(),
            a.getCreatedAt(),
            a.getUpdatedAt(),
            feats
        );
    }
}
