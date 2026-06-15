package com.wildme.wildbook_lite.ml;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wildme.wildbook_lite.annotation.Annotation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One async identification (IA) job. Maps the user-facing "I want to
 * know who this is" intent onto an asynchronous ML run.
 *
 * Lifecycle:
 *   1. Client calls POST /api/ia-tasks { annotationId } → row created
 *      with status=PENDING, no MatchResult yet.
 *   2. IaTaskRunner.run() picks the row up on a background thread,
 *      flips status to RUNNING, performs the (stub) match, then
 *      flips to DONE + populates MatchResult — OR to FAILED.
 *   3. Client polls GET /api/ia-tasks/{id} until the status is terminal.
 *
 * Why an entity row (not just an in-memory job):
 *  - Long-running jobs survive restarts. A row in PENDING after a
 *    crash can be picked up by the next runner.
 *  - The submitter can come back tomorrow and still see the result.
 *  - Audit + history get the same plumbing for free.
 *
 * Why 1:1 with MatchResult instead of inlining the candidates:
 *  - Keeps the task row narrow and indexable.
 *  - The MatchResult side can grow new columns (algorithm name,
 *    model version, thresholds) without bloating IaTask.
 */
@Entity
@Table(name = "ia_task", indexes = {
    @Index(name = "ix_ia_task_status",     columnList = "status"),
    @Index(name = "ix_ia_task_annotation", columnList = "annotation_id"),
    @Index(name = "ix_ia_task_submitter",  columnList = "submitter_user_id")
})
public class IaTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annotation_id", nullable = false)
    private Annotation annotation;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private IaTaskStatus status = IaTaskStatus.PENDING;

    /** Algorithm tag — keeps the row queryable even before the ML runs. */
    @Column(length = 64, nullable = false)
    private String algorithm = "stub-v1";

    /** Server-side timestamps. createdAt set in ctor, others by runner. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** Populated on FAILED. Truncated to TEXT for log safety. */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "submitter_user_id")
    private Long submitterUserId;

    /**
     * Optional reverse pointer to the result. We expose it inline on
     * the IaTask GET so polling clients get the candidates in the same
     * call once status flips to DONE.
     *
     * Cascade.ALL + orphanRemoval: the MatchResult belongs to the
     * IaTask lifecycle entirely — there's no scenario where a result
     * should outlive its task.
     */
    @OneToOne(mappedBy = "iaTask", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private MatchResult result;

    @Version
    private Long version;

    public IaTask() {
        this.createdAt = LocalDateTime.now();
    }

    public IaTask(Annotation annotation, Long submitterUserId) {
        this();
        this.annotation = annotation;
        this.submitterUserId = submitterUserId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Annotation getAnnotation() { return annotation; }
    public void setAnnotation(Annotation annotation) { this.annotation = annotation; }

    public IaTaskStatus getStatus() { return status; }
    public void setStatus(IaTaskStatus status) { this.status = status; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getSubmitterUserId() { return submitterUserId; }
    public void setSubmitterUserId(Long submitterUserId) { this.submitterUserId = submitterUserId; }

    public MatchResult getResult() { return result; }
    public void setResult(MatchResult result) { this.result = result; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
