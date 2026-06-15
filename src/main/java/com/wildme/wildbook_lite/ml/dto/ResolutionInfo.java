package com.wildme.wildbook_lite.ml.dto;

import java.time.LocalDateTime;

import com.wildme.wildbook_lite.ml.MatchResolution;
import com.wildme.wildbook_lite.ml.MatchResult;

/**
 * Resolution slab of the page response. Null when the task is still
 * PENDING / RUNNING (no MatchResult exists yet). Populated whenever a
 * MatchResult exists — `status` will be PENDING until a reviewer acts.
 */
public record ResolutionInfo(
    MatchResolution status,
    Long acceptedCandidateId,
    Long newIndividualId,
    LocalDateTime resolvedAt,
    Long resolvedByUserId,
    String remarks
) {

    public static ResolutionInfo from(MatchResult mr) {
        if (mr == null) return null;
        return new ResolutionInfo(
            mr.getResolution(),
            mr.getAcceptedCandidateId(),
            mr.getNewIndividualId(),
            mr.getResolvedAt(),
            mr.getResolvedByUserId(),
            mr.getRemarks()
        );
    }
}
