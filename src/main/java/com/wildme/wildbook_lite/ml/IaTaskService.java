package com.wildme.wildbook_lite.ml;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.annotation.Annotation;
import com.wildme.wildbook_lite.annotation.AnnotationRepository;
import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.project.ProjectGuard;

/**
 * Synchronous orchestration around the async IA pipeline.
 *
 * Split rationale:
 *   - IaTaskService owns the API-side contract: enqueue, cancel, query.
 *     Everything here runs on the request thread, in the request tx.
 *   - IaTaskRunner is the @Async worker: a separate bean so the
 *     @Async proxy boundary actually applies (calling an @Async method
 *     on `this` from the same bean is a no-op — proxy bypass).
 *
 * State-machine rules enforced here:
 *   PENDING → RUNNING   (runner only — internal)
 *   RUNNING → DONE      (runner only — internal)
 *   RUNNING → FAILED    (runner only — internal)
 *   PENDING → CANCELLED (user — only legal user-initiated transition)
 *
 * Anything else is rejected with a 400.
 */
@Service
public class IaTaskService {

    private final IaTaskRepository taskRepo;
    private final AnnotationRepository annotationRepo;
    private final IaTaskRunner runner;
    private final ProjectGuard projectGuard;

    public IaTaskService(IaTaskRepository taskRepo,
                         AnnotationRepository annotationRepo,
                         IaTaskRunner runner,
                         ProjectGuard projectGuard) {
        this.taskRepo = taskRepo;
        this.annotationRepo = annotationRepo;
        this.runner = runner;
        this.projectGuard = projectGuard;
    }

    @Audited("ia.enqueue")
    @Transactional
    public IaTask enqueue(Long annotationId) {
        Annotation annotation = annotationRepo.findById(annotationId)
            .orElseThrow(() -> new NotFoundException("Annotation not found: " + annotationId));
        Long projectId = annotation.getEncounter() == null
            ? null : annotation.getEncounter().getProjectId();
        if (projectId != null && !projectGuard.canWrite(projectId)) {
            throw new ForbiddenException("No write access to annotation: " + annotationId);
        }

        IaTask task = new IaTask(annotation, SecurityUtils.currentUserId());
        IaTask saved = taskRepo.save(task);

        // Fire-and-forget dispatch. We pass the id (not the entity) so
        // the runner starts a fresh persistence context — no detached
        // entity issues across the @Async boundary.
        runner.run(saved.getId());
        return saved;
    }

    @Audited("ia.cancel")
    @Transactional
    public IaTask cancel(Long id) {
        IaTask t = taskRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("IaTask not found: " + id));
        requireWriteAccess(t);

        if (t.getStatus() != IaTaskStatus.PENDING) {
            throw new BusinessException(
                "Cannot cancel task in status " + t.getStatus()
                + " (only PENDING tasks may be cancelled by users)");
        }
        t.setStatus(IaTaskStatus.CANCELLED);
        t.setEndedAt(LocalDateTime.now());
        return taskRepo.save(t);
    }

    @Transactional(readOnly = true)
    public IaTask findById(Long id) {
        IaTask t = taskRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("IaTask not found: " + id));
        requireReadAccess(t);
        // Pre-touch the lazy MatchResult so DTO mapping outside the tx
        // can see candidates without a LazyInitializationException.
        //
        // We also walk each candidate to force its lazy `individual`
        // initialization — the page DTO reads individual.nickname for
        // every candidate, and a fresh DB hit per candidate outside
        // the tx would throw LIE.
        if (t.getResult() != null) {
            for (MatchCandidate c : t.getResult().getCandidates()) {
                if (c.getIndividual() != null) c.getIndividual().getNickname();
            }
        }
        // Pre-touch the lazy annotation -> encounter chain for the
        // page DTO's queryAnnotation summary.
        if (t.getAnnotation() != null && t.getAnnotation().getEncounter() != null) {
            t.getAnnotation().getEncounter().getProjectId();
        }
        return t;
    }

    @Transactional(readOnly = true)
    public Page<IaTask> listByAnnotation(Long annotationId, Pageable pageable) {
        Annotation annotation = annotationRepo.findById(annotationId)
            .orElseThrow(() -> new NotFoundException("Annotation not found: " + annotationId));
        Long projectId = annotation.getEncounter() == null
            ? null : annotation.getEncounter().getProjectId();
        if (projectId != null && !projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to annotation: " + annotationId);
        }
        return taskRepo.findByAnnotationIdOrderByCreatedAtDesc(annotationId, pageable);
    }

    // ----- access helpers -----

    private void requireReadAccess(IaTask t) {
        Long projectId = projectIdOf(t);
        if (projectId != null && !projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to ia-task: " + t.getId());
        }
    }

    private void requireWriteAccess(IaTask t) {
        Long projectId = projectIdOf(t);
        if (projectId != null && !projectGuard.canWrite(projectId)) {
            throw new ForbiddenException("No write access to ia-task: " + t.getId());
        }
    }

    private Long projectIdOf(IaTask t) {
        if (t.getAnnotation() == null || t.getAnnotation().getEncounter() == null) {
            return null;
        }
        return t.getAnnotation().getEncounter().getProjectId();
    }
}
