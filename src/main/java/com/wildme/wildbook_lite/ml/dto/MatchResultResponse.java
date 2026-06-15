package com.wildme.wildbook_lite.ml.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MatchResultResponse(
    Long id,
    String algorithm,
    Double topScore,
    LocalDateTime createdAt,
    List<MatchCandidateResponse> candidates
) {}
