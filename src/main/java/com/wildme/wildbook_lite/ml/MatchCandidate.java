package com.wildme.wildbook_lite.ml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wildme.wildbook_lite.entity.Individual;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One row of the candidate-list output by the matcher.
 *
 *   (annotation A) × (individual I) → score, rank
 *
 * Score is normalized 0..1; rank is the position in the sorted result
 * (1 = best). We store rank explicitly so reading the list back is a
 * single SELECT with ORDER BY rank, no scanning of scores.
 */
@Entity
@Table(name = "match_candidate", indexes = {
    @Index(name = "ix_candidate_result",     columnList = "match_result_id"),
    @Index(name = "ix_candidate_individual", columnList = "individual_id")
})
public class MatchCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_result_id", nullable = false)
    private MatchResult matchResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "individual_id", nullable = false)
    private Individual individual;

    /** Normalized 0..1 similarity. */
    @Column(nullable = false)
    private Double score;

    /** Position in the sorted result, 1-based. */
    @Column(name = "rank_position", nullable = false)
    private Integer rank;

    public MatchCandidate() {}

    public MatchCandidate(MatchResult matchResult, Individual individual, double score, int rank) {
        this.matchResult = matchResult;
        this.individual = individual;
        this.score = score;
        this.rank = rank;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MatchResult getMatchResult() { return matchResult; }
    public void setMatchResult(MatchResult matchResult) { this.matchResult = matchResult; }

    public Individual getIndividual() { return individual; }
    public void setIndividual(Individual individual) { this.individual = individual; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
}
