package com.wildme.wildbook_lite.ml.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.wildme.wildbook_lite.ml.IaTask;
import com.wildme.wildbook_lite.ml.IaTaskStatus;
import com.wildme.wildbook_lite.ml.MatchCandidate;
import com.wildme.wildbook_lite.ml.MatchResult;

/**
 * One-shot page payload for the match results screen.
 *
 * Bundles everything the UI needs:
 *   - task status (so the page can show "still running, polling...")
 *   - query annotation summary (the photo being matched)
 *   - top N ranked candidates with score + Individual id/nickname
 *   - resolution state (PENDING / ACCEPTED / REJECTED_NEW / SKIPPED)
 *
 * Why a dedicated page DTO vs reusing IaTaskResponse:
 *   - IaTaskResponse is for the polling endpoint and embeds the full
 *     unfiltered candidate list. This page DTO supports topN cap
 *     (default 5) so a "result with 50 candidates" doesn't blow the
 *     payload. It also enriches candidates with individual nicknames
 *     in one place rather than asking the client to fan-out N lookups.
 */
public record MatchResultPageResponse(
    Long taskId,
    IaTaskStatus status,
    String algorithm,
    LocalDateTime taskCreatedAt,
    LocalDateTime taskStartedAt,
    LocalDateTime taskEndedAt,
    String errorMessage,

    QueryAnnotationSummary queryAnnotation,

    /** Null until the ML run finishes. */
    Long matchResultId,
    Double topScore,
    LocalDateTime matchResultCreatedAt,
    List<MatchCandidateResponse> candidates,

    /** Null until a MatchResult row exists; otherwise reflects PENDING/ACCEPTED/... */
    ResolutionInfo resolution
) {

    /**
     * @param topN  hard cap on returned candidates. Caller passes 5 by
     *              default. Pass Integer.MAX_VALUE to fetch all.
     */
    public static MatchResultPageResponse from(IaTask task, int topN) {
        MatchResult mr = task.getResult();

        List<MatchCandidateResponse> trimmed = List.of();
        Long mrId = null;
        Double top = null;
        LocalDateTime mrCreated = null;

        if (mr != null) {
            mrId = mr.getId();
            top = mr.getTopScore();
            mrCreated = mr.getCreatedAt();

            List<MatchCandidate> sorted = mr.getCandidates();
            if (sorted != null) {
                trimmed = sorted.stream()
                    .sorted((a, b) -> Integer.compare(
                        a.getRank() == null ? Integer.MAX_VALUE : a.getRank(),
                        b.getRank() == null ? Integer.MAX_VALUE : b.getRank()))
                    .limit(topN)
                    .map(MatchCandidateResponse::from)
                    .collect(Collectors.toList());
            }
        }

        return new MatchResultPageResponse(
            task.getId(),
            task.getStatus(),
            task.getAlgorithm(),
            task.getCreatedAt(),
            task.getStartedAt(),
            task.getEndedAt(),
            task.getErrorMessage(),
            QueryAnnotationSummary.from(task.getAnnotation()),
            mrId,
            top,
            mrCreated,
            trimmed,
            ResolutionInfo.from(mr)
        );
    }
}
