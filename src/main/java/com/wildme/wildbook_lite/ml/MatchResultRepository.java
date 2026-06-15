package com.wildme.wildbook_lite.ml;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    Optional<MatchResult> findByIaTaskId(Long iaTaskId);
}
