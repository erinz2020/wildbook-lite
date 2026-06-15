package com.wildme.wildbook_lite.ml.dto;

import com.wildme.wildbook_lite.ml.MatchCandidate;

public record MatchCandidateResponse(
    Long id,
    Long individualId,
    String individualNickname,
    Double score,
    Integer rank
) {
    public static MatchCandidateResponse from(MatchCandidate c) {
        return new MatchCandidateResponse(
            c.getId(),
            c.getIndividual() == null ? null : c.getIndividual().getId(),
            c.getIndividual() == null ? null : c.getIndividual().getNickname(),
            c.getScore(),
            c.getRank()
        );
    }
}
