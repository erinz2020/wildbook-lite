package com.wildme.wildbook_lite.ml.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.wildme.wildbook_lite.ml.IaTask;
import com.wildme.wildbook_lite.ml.IaTaskStatus;
import com.wildme.wildbook_lite.ml.MatchResult;

public record IaTaskResponse(
    Long id,
    Long annotationId,
    IaTaskStatus status,
    String algorithm,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    String errorMessage,
    Long submitterUserId,
    MatchResultResponse result
) {

    public static IaTaskResponse from(IaTask t) {
        MatchResultResponse mr = null;
        MatchResult result = t.getResult();
        if (result != null) {
            List<MatchCandidateResponse> cands = result.getCandidates() == null ? List.of()
                : result.getCandidates().stream()
                    .map(MatchCandidateResponse::from)
                    .collect(Collectors.toList());
            mr = new MatchResultResponse(
                result.getId(),
                result.getAlgorithm(),
                result.getTopScore(),
                result.getCreatedAt(),
                cands
            );
        }
        return new IaTaskResponse(
            t.getId(),
            t.getAnnotation() == null ? null : t.getAnnotation().getId(),
            t.getStatus(),
            t.getAlgorithm(),
            t.getCreatedAt(),
            t.getStartedAt(),
            t.getEndedAt(),
            t.getErrorMessage(),
            t.getSubmitterUserId(),
            mr
        );
    }
}
