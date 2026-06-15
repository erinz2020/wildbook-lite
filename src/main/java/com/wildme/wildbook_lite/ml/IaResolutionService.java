package com.wildme.wildbook_lite.ml;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.annotation.Annotation;
import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.ml.dto.AcceptMatchRequest;
import com.wildme.wildbook_lite.ml.dto.CreateIndividualFromMatchRequest;
import com.wildme.wildbook_lite.ml.dto.SkipMatchRequest;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.repository.IndividualRepository;
import com.wildme.wildbook_lite.search.opensearch.EncounterChangedEvent;

/**
 * Reviewer-decision side of the IA pipeline. Once
 * {@link IaTaskRunner} has produced a MatchResult, a human looks at
 * the top candidates and lands on one of three terminal outcomes —
 * accept, create-new-individual, skip — recorded here.
 *
 * Pre-conditions for ALL actions:
 *   - Task exists and has status = DONE (you can't accept candidates
 *     while the matcher is still running).
 *   - A MatchResult row exists (DONE implies this, but we double-check
 *     defensively).
 *   - Resolution is currently PENDING — terminal states are
 *     immutable. (Caller can build a "re-open" flow later if needed
 *     but it's not free — it'd need a separate state arrow.)
 *   - Caller has WRITE access to the project that owns the query
 *     annotation's encounter.
 *
 * Side effects shared by accept + create-individual:
 *   - encounter.individual is set (or replaced).
 *   - encounter.updatedAt bumped via repo.save.
 *   - EncounterChangedEvent(UPSERT) fired so OS index resyncs.
 *
 * Skip does NOT touch the encounter — pure audit fact.
 */
@Service
public class IaResolutionService {

    private final IaTaskRepository taskRepo;
    private final MatchCandidateRepository candidateRepo;
    private final IndividualRepository individualRepo;
    private final EncounterRepository encounterRepo;
    private final ProjectGuard projectGuard;
    private final ApplicationEventPublisher events;

    public IaResolutionService(IaTaskRepository taskRepo,
                               MatchCandidateRepository candidateRepo,
                               IndividualRepository individualRepo,
                               EncounterRepository encounterRepo,
                               ProjectGuard projectGuard,
                               ApplicationEventPublisher events) {
        this.taskRepo = taskRepo;
        this.candidateRepo = candidateRepo;
        this.individualRepo = individualRepo;
        this.encounterRepo = encounterRepo;
        this.projectGuard = projectGuard;
        this.events = events;
    }

    @Audited("ia.accept")
    @Transactional
    public IaTask accept(Long taskId, AcceptMatchRequest req) {
        IaTask task = loadDoneTask(taskId);
        MatchResult mr = requirePendingResolution(task);
        Encounter encounter = requireWriteAccessAndEncounter(task);

        MatchCandidate candidate = candidateRepo.findById(req.candidateId())
            .orElseThrow(() -> new NotFoundException(
                "MatchCandidate not found: " + req.candidateId()));

        // The candidate must belong to THIS task's result — accepting a
        // candidate from a different task would silently associate the
        // wrong individual.
        if (candidate.getMatchResult() == null
            || !mr.getId().equals(candidate.getMatchResult().getId())) {
            throw new BusinessException(
                "Candidate " + req.candidateId()
                + " does not belong to match result " + mr.getId());
        }
        Individual individual = candidate.getIndividual();
        if (individual == null) {
            throw new BusinessException(
                "Candidate " + req.candidateId() + " has no Individual attached");
        }

        // Species coherence — same guard the encounter PATCH applies.
        String encSpecies = encounter.getSpecies();
        if (encSpecies != null && individual.getSpecies() != null
            && !encSpecies.equalsIgnoreCase(individual.getSpecies())) {
            throw new BusinessException(
                "Species mismatch: encounter='" + encSpecies
                + "' vs candidate individual='" + individual.getSpecies() + "'");
        }

        encounter.setIndividual(individual);
        encounterRepo.save(encounter);

        mr.setResolution(MatchResolution.ACCEPTED);
        mr.setAcceptedCandidateId(candidate.getId());
        mr.setResolvedAt(LocalDateTime.now());
        mr.setResolvedByUserId(SecurityUtils.currentUserId());
        if (req.remarks() != null) mr.setRemarks(req.remarks());

        events.publishEvent(new EncounterChangedEvent(
            encounter.getId(), EncounterChangedEvent.Kind.UPSERT));
        return task;
    }

    @Audited("ia.createIndividual")
    @Transactional
    public IaTask createIndividualFromQuery(Long taskId,
                                            CreateIndividualFromMatchRequest req) {
        IaTask task = loadDoneTask(taskId);
        MatchResult mr = requirePendingResolution(task);
        Encounter encounter = requireWriteAccessAndEncounter(task);

        Individual ind = new Individual();
        ind.setNickname(req.nickname().trim());
        if (req.sex() != null && !req.sex().isBlank()) ind.setSex(req.sex().trim());
        // Adopt the encounter's species so future species-mismatch
        // checks succeed for further encounters of the same animal.
        ind.setSpecies(encounter.getSpecies());
        Individual saved = individualRepo.save(ind);

        encounter.setIndividual(saved);
        encounterRepo.save(encounter);

        mr.setResolution(MatchResolution.REJECTED_NEW_INDIVIDUAL);
        mr.setNewIndividualId(saved.getId());
        mr.setResolvedAt(LocalDateTime.now());
        mr.setResolvedByUserId(SecurityUtils.currentUserId());
        if (req.remarks() != null) mr.setRemarks(req.remarks());

        events.publishEvent(new EncounterChangedEvent(
            encounter.getId(), EncounterChangedEvent.Kind.UPSERT));
        return task;
    }

    @Audited("ia.skip")
    @Transactional
    public IaTask skip(Long taskId, SkipMatchRequest req) {
        IaTask task = loadDoneTask(taskId);
        MatchResult mr = requirePendingResolution(task);
        // SKIP requires read access — we're just adding a note, not
        // modifying the encounter — so canRead is the right gate.
        Long projectId = projectIdOf(task);
        if (projectId != null && !projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No access to ia-task: " + task.getId());
        }

        mr.setResolution(MatchResolution.SKIPPED);
        mr.setResolvedAt(LocalDateTime.now());
        mr.setResolvedByUserId(SecurityUtils.currentUserId());
        if (req != null && req.remarks() != null) mr.setRemarks(req.remarks());

        // No encounter mutation → no EncounterChangedEvent.
        return task;
    }

    // ----- helpers -----

    private IaTask loadDoneTask(Long taskId) {
        IaTask task = taskRepo.findById(taskId)
            .orElseThrow(() -> new NotFoundException("IaTask not found: " + taskId));
        if (task.getStatus() != IaTaskStatus.DONE) {
            throw new BusinessException(
                "Cannot resolve task in status " + task.getStatus()
                + " (must be DONE)");
        }
        // Pre-touch the lazy MatchResult + every candidate's individual
        // while we're still inside the tx, so the page DTO mapping that
        // runs after this service returns doesn't trip LIE.
        if (task.getResult() != null) {
            for (MatchCandidate c : task.getResult().getCandidates()) {
                if (c.getIndividual() != null) c.getIndividual().getNickname();
            }
        }
        if (task.getAnnotation() != null && task.getAnnotation().getEncounter() != null) {
            task.getAnnotation().getEncounter().getProjectId();
        }
        return task;
    }

    private MatchResult requirePendingResolution(IaTask task) {
        MatchResult mr = task.getResult();
        if (mr == null) {
            throw new BusinessException("Task " + task.getId() + " has no MatchResult");
        }
        if (mr.getResolution() != MatchResolution.PENDING) {
            throw new BusinessException(
                "Task " + task.getId() + " is already resolved as "
                + mr.getResolution() + "; resolutions are immutable");
        }
        return mr;
    }

    private Encounter requireWriteAccessAndEncounter(IaTask task) {
        Annotation a = task.getAnnotation();
        Encounter e = (a == null) ? null : a.getEncounter();
        if (e == null) {
            throw new BusinessException(
                "Task " + task.getId() + " has no associated encounter");
        }
        if (e.getProjectId() != null && !projectGuard.canWrite(e.getProjectId())) {
            throw new ForbiddenException("No write access to encounter: " + e.getId());
        }
        return e;
    }

    private Long projectIdOf(IaTask task) {
        if (task.getAnnotation() == null || task.getAnnotation().getEncounter() == null) {
            return null;
        }
        return task.getAnnotation().getEncounter().getProjectId();
    }
}
