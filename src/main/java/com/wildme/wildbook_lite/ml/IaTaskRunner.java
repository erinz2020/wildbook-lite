package com.wildme.wildbook_lite.ml;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.annotation.Annotation;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.repository.IndividualRepository;

/**
 * The async worker. Picks up an IaTask by id (passed from
 * IaTaskService.enqueue) and runs the stub matching algorithm.
 *
 * Why a SEPARATE bean from IaTaskService:
 *  - @Async + @Transactional only work through Spring's proxy. If
 *    IaTaskService called its own @Async method on `this`, the call
 *    would bypass the proxy → run synchronously → ignore @Transactional.
 *    Splitting into a distinct bean dodges that footgun.
 *
 * Why Propagation.REQUIRES_NEW on `markRunning` and `markDone/Failed`:
 *  - We want each state change to commit independently. If the actual
 *    matching throws, the RUNNING marker still commits (so the user
 *    sees "RUNNING ... then FAILED" not "still PENDING after crash").
 *
 * The "matcher" is deliberately fake: pick up to 5 individuals whose
 * species matches the annotation's encounter, give them descending
 * scores. Good enough to teach the pipeline shape without needing real
 * ML infra.
 */
@Component
public class IaTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(IaTaskRunner.class);
    private static final int MAX_CANDIDATES = 5;
    /** Simulated work duration. Long enough to demo the PENDING→RUNNING→DONE flow. */
    private static final long SIMULATED_WORK_MS = 1500;

    private final IaTaskRepository taskRepo;
    private final IndividualRepository individualRepo;
    private final Random random = new Random();

    public IaTaskRunner(IaTaskRepository taskRepo,
                        IndividualRepository individualRepo) {
        this.taskRepo = taskRepo;
        this.individualRepo = individualRepo;
    }

    /**
     * Entry point — called by IaTaskService.enqueue. Runs on a
     * background thread (see AsyncConfig.applicationTaskExecutor).
     *
     * The outermost method is intentionally NOT @Transactional: we
     * want each state-change to be its own short tx, otherwise a
     * single long tx would hold a DB connection for the whole
     * simulated work period.
     */
    @Async("applicationTaskExecutor")
    public void run(Long taskId) {
        log.info("[ia] starting task {}", taskId);
        try {
            if (!markRunning(taskId)) {
                // Task was cancelled before we could pick it up.
                return;
            }
            // Stand-in for the real ML pass.
            Thread.sleep(SIMULATED_WORK_MS);
            markDone(taskId, computeCandidates(taskId));
            log.info("[ia] finished task {}", taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(taskId, "Interrupted: " + e.getMessage());
        } catch (Exception e) {
            log.warn("[ia] task {} failed: {}", taskId, e.toString());
            markFailed(taskId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Returns true if PENDING → RUNNING happened. Returns false if the
     * task no longer exists OR is not in PENDING (e.g., cancelled).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected boolean markRunning(Long taskId) {
        IaTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null) return false;
        if (t.getStatus() != IaTaskStatus.PENDING) return false;
        t.setStatus(IaTaskStatus.RUNNING);
        t.setStartedAt(LocalDateTime.now());
        taskRepo.save(t);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markDone(Long taskId, List<Candidate> candidates) {
        IaTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null || t.getStatus() != IaTaskStatus.RUNNING) return;

        MatchResult mr = new MatchResult(t, t.getAlgorithm());
        double top = 0.0;
        int rank = 1;
        for (Candidate c : candidates) {
            mr.getCandidates().add(new MatchCandidate(mr, c.individual(), c.score(), rank++));
            top = Math.max(top, c.score());
        }
        mr.setTopScore(top);
        t.setResult(mr);

        t.setStatus(IaTaskStatus.DONE);
        t.setEndedAt(LocalDateTime.now());
        taskRepo.save(t);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markFailed(Long taskId, String message) {
        IaTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null) return;
        t.setStatus(IaTaskStatus.FAILED);
        t.setEndedAt(LocalDateTime.now());
        t.setErrorMessage(message);
        taskRepo.save(t);
    }

    /**
     * Stub matcher: pick up to 5 Individuals that share the species of
     * the annotation's encounter, score them with descending pseudo-
     * random similarities. In a real system this would call the IBEIS
     * / Sage / WBIA service over HTTP and parse the candidate list.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    protected List<Candidate> computeCandidates(Long taskId) {
        IaTask t = taskRepo.findById(taskId).orElseThrow();
        Annotation a = t.getAnnotation();
        String species = (a == null || a.getEncounter() == null)
            ? null : a.getEncounter().getSpecies();

        Specification<Individual> spec = (species == null)
            ? null
            : (root, q, cb) -> cb.equal(cb.lower(root.get("species")), species.toLowerCase());

        List<Individual> pool = spec == null
            ? individualRepo.findAll()
            : individualRepo.findAll(spec);

        // top-5 by some pseudo-similarity. Real matcher would have
        // actual scores; here we sort by a deterministic-ish noise.
        return pool.stream()
            .map(i -> new Candidate(i, 0.5 + random.nextDouble() * 0.5))
            .sorted(Comparator.comparingDouble(Candidate::score).reversed())
            .limit(MAX_CANDIDATES)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** Local intermediate value object — Individual + score, before we have a MatchResult to attach it to. */
    protected record Candidate(Individual individual, double score) {}
}
