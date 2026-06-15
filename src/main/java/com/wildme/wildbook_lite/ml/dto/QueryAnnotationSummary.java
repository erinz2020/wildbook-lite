package com.wildme.wildbook_lite.ml.dto;

import com.wildme.wildbook_lite.annotation.Annotation;
import com.wildme.wildbook_lite.annotation.Viewpoint;

/**
 * Lightweight description of the query annotation — the "this image,
 * who is it?" half of the page. We deliberately don't return the full
 * Annotation entity to keep the page payload small.
 */
public record QueryAnnotationSummary(
    Long id,
    Long encounterId,
    Long projectId,
    String species,
    Viewpoint viewpoint
) {

    public static QueryAnnotationSummary from(Annotation a) {
        if (a == null) return null;
        return new QueryAnnotationSummary(
            a.getId(),
            a.getEncounter() == null ? null : a.getEncounter().getId(),
            a.getEncounter() == null ? null : a.getEncounter().getProjectId(),
            a.getSpecies(),
            a.getViewpoint()
        );
    }
}
