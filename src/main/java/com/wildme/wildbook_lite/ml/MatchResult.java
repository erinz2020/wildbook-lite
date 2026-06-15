package com.wildme.wildbook_lite.ml;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * Reviewer decision state — see {@link MatchResolution} for the
     * lifecycle. Starts PENDING; flipped by IaResolutionService
     * when a reviewer accepts / rejects / skips.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MatchResolution resolution = MatchResolution.PENDING;

    /**
     * Just an id (not a JPA relation) to dodge the circular FK loop
     * (MatchResult -> MatchCandidate, and MatchCandidate -> MatchResult).
     * Service looks the candidate up by id when it needs it.
     */
    @Column(name = "accepted_candidate_id")
    private Long acceptedCandidateId;

    /**
     * Just an id pointer to Individual for the same reason — also
     * keeps the entity navigation graph from leaking into permission
     * checks. Populated when resolution = REJECTED_NEW_INDIVIDUAL.
     */
    @Column(name = "new_individual_id")
    private Long newIndividualId;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    /** Free-text reviewer note (e.g., "distinctive fluke pattern"). */
    @Column(columnDefinition = "text")
    private String remarks;

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

    public MatchResolution getResolution() { return resolution; }
    public void setResolution(MatchResolution resolution) { this.resolution = resolution; }

    public Long getAcceptedCandidateId() { return acceptedCandidateId; }
    public void setAcceptedCandidateId(Long acceptedCandidateId) { this.acceptedCandidateId = acceptedCandidateId; }

    public Long getNewIndividualId() { return newIndividualId; }
    public void setNewIndividualId(Long newIndividualId) { this.newIndividualId = newIndividualId; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public Long getResolvedByUserId() { return resolvedByUserId; }
    public void setResolvedByUserId(Long resolvedByUserId) { this.resolvedByUserId = resolvedByUserId; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
