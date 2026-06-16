package com.wildme.wildbook_lite.ml.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wildme.wildbook_lite.annotation.Annotation;
import com.wildme.wildbook_lite.annotation.Viewpoint;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.ml.IaTask;
import com.wildme.wildbook_lite.ml.IaTaskStatus;
import com.wildme.wildbook_lite.ml.MatchCandidate;
import com.wildme.wildbook_lite.ml.MatchResolution;
import com.wildme.wildbook_lite.ml.MatchResult;

/**
 * Pure DTO test — no mocks, no Spring. Verifies the assembly logic of
 * {@link MatchResultPageResponse#from(IaTask, int)}:
 *
 *   - topN trimming honours rank order, not insertion order
 *   - candidates with null rank are pushed to the back
 *   - PENDING / RUNNING tasks (no MatchResult yet) produce a payload
 *     with null result fields + empty candidate list + null resolution
 *   - DONE task with MatchResult produces fully populated fields
 *   - resolution status pass-through (PENDING / ACCEPTED / SKIPPED)
 */
class MatchResultPageResponseTest {

    // ---------- topN trimming ----------

    @Test
    @DisplayName("trims candidates to topN preserving rank order regardless of insertion order")
    void trimsRespectingRank() {
        IaTask task = doneTask();
        MatchResult mr = task.getResult();
        // Add 5 candidates out of rank order (3, 1, 4, 2, 5)
        mr.getCandidates().addAll(List.of(
            candidate(mr, "C3", 0.80, 3),
            candidate(mr, "C1", 0.94, 1),
            candidate(mr, "C4", 0.70, 4),
            candidate(mr, "C2", 0.88, 2),
            candidate(mr, "C5", 0.60, 5)
        ));

        MatchResultPageResponse page = MatchResultPageResponse.from(task, 3);

        assertThat(page.candidates()).hasSize(3);
        assertThat(page.candidates()).extracting(MatchCandidateResponse::rank)
            .containsExactly(1, 2, 3);
        assertThat(page.candidates()).extracting(MatchCandidateResponse::individualNickname)
            .containsExactly("C1", "C2", "C3");
    }

    @Test
    @DisplayName("topN larger than candidate count returns all candidates")
    void topNExceedsCount() {
        IaTask task = doneTask();
        task.getResult().getCandidates().addAll(List.of(
            candidate(task.getResult(), "C1", 0.9, 1),
            candidate(task.getResult(), "C2", 0.8, 2)
        ));

        MatchResultPageResponse page = MatchResultPageResponse.from(task, 50);

        assertThat(page.candidates()).hasSize(2);
    }

    @Test
    @DisplayName("candidates with null rank are sorted last")
    void nullRankSortsLast() {
        IaTask task = doneTask();
        MatchResult mr = task.getResult();
        mr.getCandidates().addAll(List.of(
            candidate(mr, "C-null", 0.99, null),
            candidate(mr, "C1", 0.9, 1)
        ));

        MatchResultPageResponse page = MatchResultPageResponse.from(task, 2);

        assertThat(page.candidates()).extracting(MatchCandidateResponse::individualNickname)
            .containsExactly("C1", "C-null");
    }

    // ---------- null safety: PENDING / RUNNING tasks ----------

    @Test
    @DisplayName("RUNNING task (no MatchResult yet) → result fields null, candidates empty, resolution null")
    void noResultYet() {
        IaTask task = new IaTask();
        task.setId(42L);
        task.setStatus(IaTaskStatus.RUNNING);
        task.setAlgorithm("stub-v1");
        task.setCreatedAt(LocalDateTime.now());
        task.setStartedAt(LocalDateTime.now());

        MatchResultPageResponse page = MatchResultPageResponse.from(task, 5);

        assertThat(page.taskId()).isEqualTo(42L);
        assertThat(page.status()).isEqualTo(IaTaskStatus.RUNNING);
        assertThat(page.matchResultId()).isNull();
        assertThat(page.topScore()).isNull();
        assertThat(page.matchResultCreatedAt()).isNull();
        assertThat(page.candidates()).isEmpty();
        assertThat(page.resolution()).isNull();
        // Annotation also absent — should not throw NPE.
        assertThat(page.queryAnnotation()).isNull();
    }

    @Test
    @DisplayName("FAILED task with errorMessage exposes it to the page")
    void failedTaskCarriesError() {
        IaTask task = new IaTask();
        task.setId(43L);
        task.setStatus(IaTaskStatus.FAILED);
        task.setAlgorithm("stub-v1");
        task.setErrorMessage("WBIA upstream timeout");
        task.setCreatedAt(LocalDateTime.now());
        task.setEndedAt(LocalDateTime.now());

        MatchResultPageResponse page = MatchResultPageResponse.from(task, 5);

        assertThat(page.errorMessage()).isEqualTo("WBIA upstream timeout");
        assertThat(page.status()).isEqualTo(IaTaskStatus.FAILED);
    }

    // ---------- queryAnnotation enrichment ----------

    @Test
    @DisplayName("queryAnnotation summary populated from task.annotation when present")
    void queryAnnotationSummary() {
        IaTask task = doneTask();   // annotation -> encounter wired by helper

        MatchResultPageResponse page = MatchResultPageResponse.from(task, 5);

        QueryAnnotationSummary qa = page.queryAnnotation();
        assertThat(qa).isNotNull();
        assertThat(qa.id()).isEqualTo(10L);
        assertThat(qa.encounterId()).isEqualTo(100L);
        assertThat(qa.projectId()).isEqualTo(1L);
        assertThat(qa.species()).isEqualTo("Humpback whale");
        assertThat(qa.viewpoint()).isEqualTo(Viewpoint.LEFT);
    }

    // ---------- resolution status pass-through ----------

    @Test
    @DisplayName("resolution=PENDING reflected on the page")
    void resolutionPendingPropagates() {
        IaTask task = doneTask();   // MatchResult.resolution defaults to PENDING

        MatchResultPageResponse page = MatchResultPageResponse.from(task, 5);

        assertThat(page.resolution()).isNotNull();
        assertThat(page.resolution().status()).isEqualTo(MatchResolution.PENDING);
        assertThat(page.resolution().acceptedCandidateId()).isNull();
        assertThat(page.resolution().resolvedAt()).isNull();
    }

    @Test
    @DisplayName("resolution=ACCEPTED with all decision fields propagates")
    void resolutionAcceptedPropagates() {
        IaTask task = doneTask();
        MatchResult mr = task.getResult();
        mr.setResolution(MatchResolution.ACCEPTED);
        mr.setAcceptedCandidateId(77L);
        mr.setResolvedAt(LocalDateTime.of(2026, 6, 15, 12, 0));
        mr.setResolvedByUserId(99L);
        mr.setRemarks("clear fluke match");

        MatchResultPageResponse page = MatchResultPageResponse.from(task, 5);

        ResolutionInfo r = page.resolution();
        assertThat(r.status()).isEqualTo(MatchResolution.ACCEPTED);
        assertThat(r.acceptedCandidateId()).isEqualTo(77L);
        assertThat(r.resolvedByUserId()).isEqualTo(99L);
        assertThat(r.remarks()).isEqualTo("clear fluke match");
    }

    // ---------- helpers ----------

    private IaTask doneTask() {
        Encounter enc = new Encounter();
        enc.setId(100L);
        enc.setProjectId(1L);
        enc.setSpecies("Humpback whale");

        Annotation ann = new Annotation();
        ann.setId(10L);
        ann.setEncounter(enc);
        ann.setSpecies("Humpback whale");
        ann.setViewpoint(Viewpoint.LEFT);

        IaTask task = new IaTask();
        task.setId(42L);
        task.setAnnotation(ann);
        task.setStatus(IaTaskStatus.DONE);
        task.setAlgorithm("stub-v1");
        task.setCreatedAt(LocalDateTime.now());

        MatchResult mr = new MatchResult(task, "stub-v1");
        mr.setId(1L);
        mr.setTopScore(0.94);
        mr.setCreatedAt(LocalDateTime.now());
        // Caller adds candidates as needed; pre-init the mutable list.
        mr.setCandidates(new ArrayList<>());
        task.setResult(mr);
        return task;
    }

    private MatchCandidate candidate(MatchResult mr, String nickname, double score, Integer rank) {
        Individual ind = new Individual();
        ind.setId((long) nickname.hashCode());
        ind.setNickname(nickname);
        MatchCandidate c = new MatchCandidate(mr, ind, score, rank == null ? 0 : rank);
        c.setRank(rank);
        return c;
    }
}
