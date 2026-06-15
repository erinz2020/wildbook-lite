package com.wildme.wildbook_lite.ml;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * The output of an IA task. Holds the algorithm metadata and a sorted
 * list of MatchCandidates (annotation × candidate individual × score).
 *
 * Why a separate entity:
 *  - Lets us re-run an ML task with a different algorithm without
 *    losing the previous result (we'd create a new IaTask + MatchResult
 *    rather than overwrite).
 *  - Keeps the IaTask row narrow — important because we index by
 *    status and submitter, and a fat row hurts the index.
 */
@Entity
@Table(name = "match_result")
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ia_task_id", nullable = false, unique = true)
    private IaTask iaTask;

    @Column(length = 64, nullable = false)
    private String algorithm;

    /** Denormalized top score, so we can sort tasks by best match without joining MatchCandidate. */
    @Column(name = "top_score")
    private Double topScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Ordered list of ranked candidates. CascadeType.ALL + orphanRemoval
     * because candidates only exist as part of one MatchResult.
     */
    @OneToMany(mappedBy = "matchResult",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<MatchCandidate> candidates = new ArrayList<>();

    public MatchResult() {
        this.createdAt = LocalDateTime.now();
    }

    public MatchResult(IaTask iaTask, String algorithm) {
        this();
        this.iaTask = iaTask;
        this.algorithm = algorithm;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public IaTask getIaTask() { return iaTask; }
    public void setIaTask(IaTask iaTask) { this.iaTask = iaTask; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public Double getTopScore() { return topScore; }
    public void setTopScore(Double topScore) { this.topScore = topScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<MatchCandidate> getCandidates() { return candidates; }
    public void setCandidates(List<MatchCandidate> candidates) { this.candidates = candidates; }
}
