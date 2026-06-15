package com.wildme.wildbook_lite.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wildme.wildbook_lite.annotation.Annotation;
import com.wildme.wildbook_lite.auth.AppPrincipal;
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
 * Unit tests for the reviewer-decision side of the IA pipeline.
 *
 * Strategy: build a small object graph by hand (Annotation -> Encounter,
 * MatchResult with N candidates) so the service operates on realistic
 * data, but the persistence is all Mockito stubs.
 *
 * SecurityContext is set in @BeforeEach so SecurityUtils.currentUserId()
 * inside the service returns a known user.
 */
@ExtendWith(MockitoExtension.class)
class IaResolutionServiceTest {

    @Mock IaTaskRepository taskRepo;
    @Mock MatchCandidateRepository candidateRepo;
    @Mock IndividualRepository individualRepo;
    @Mock EncounterRepository encounterRepo;
    @Mock ProjectGuard projectGuard;
    @Mock ApplicationEventPublisher events;

    @InjectMocks
    IaResolutionService svc;

    private static final Long PROJECT_ID = 1L;
    private static final Long ENCOUNTER_ID = 100L;
    private static final Long TASK_ID = 42L;
    private static final Long CANDIDATE_ID = 7L;
    private static final Long INDIVIDUAL_ID = 11L;
    private static final Long CURRENT_USER_ID = 99L;

    @BeforeEach
    void login() {
        AppPrincipal principal = new AppPrincipal(
            CURRENT_USER_ID, "reviewer", "ignored", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    // ---------- accept ----------

    @Nested
    @DisplayName("accept")
    class Accept {

        @Test
        @DisplayName("happy path: encounter.individual set, resolution=ACCEPTED, event fired")
        void happyPath() {
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Humpback whale");
            MatchResult mr = task.getResult();
            MatchCandidate cand = mr.getCandidates().get(0);

            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);
            when(candidateRepo.findById(CANDIDATE_ID)).thenReturn(Optional.of(cand));

            IaTask result = svc.accept(TASK_ID, new AcceptMatchRequest(CANDIDATE_ID, "looks right"));

            // encounter.individual was set to candidate's individual
            ArgumentCaptor<Encounter> encCap = ArgumentCaptor.forClass(Encounter.class);
            verify(encounterRepo).save(encCap.capture());
            assertThat(encCap.getValue().getIndividual().getId()).isEqualTo(INDIVIDUAL_ID);

            // resolution recorded
            assertThat(mr.getResolution()).isEqualTo(MatchResolution.ACCEPTED);
            assertThat(mr.getAcceptedCandidateId()).isEqualTo(CANDIDATE_ID);
            assertThat(mr.getResolvedByUserId()).isEqualTo(CURRENT_USER_ID);
            assertThat(mr.getResolvedAt()).isNotNull();
            assertThat(mr.getRemarks()).isEqualTo("looks right");

            // OS index sync event
            ArgumentCaptor<EncounterChangedEvent> ev = ArgumentCaptor.forClass(EncounterChangedEvent.class);
            verify(events, times(1)).publishEvent(ev.capture());
            assertThat(ev.getValue().kind()).isEqualTo(EncounterChangedEvent.Kind.UPSERT);
            assertThat(ev.getValue().encounterId()).isEqualTo(ENCOUNTER_ID);

            assertThat(result).isSameAs(task);
        }

        @Test
        @DisplayName("task not DONE → BusinessException (no DB writes)")
        void taskMustBeDone() {
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Humpback whale");
            task.setStatus(IaTaskStatus.RUNNING);
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));

            assertThatThrownBy(() -> svc.accept(TASK_ID, new AcceptMatchRequest(CANDIDATE_ID, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be DONE");

            verify(encounterRepo, never()).save(any());
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("resolution already ACCEPTED → BusinessException (immutable)")
        void resolutionImmutable() {
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Humpback whale");
            task.getResult().setResolution(MatchResolution.ACCEPTED);
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));

            assertThatThrownBy(() -> svc.accept(TASK_ID, new AcceptMatchRequest(CANDIDATE_ID, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already resolved");

            verify(encounterRepo, never()).save(any());
        }

        @Test
        @DisplayName("candidate belongs to a different task → BusinessException")
        void candidateFromWrongTask() {
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Humpback whale");

            // Build an orphan candidate pointing at a DIFFERENT MatchResult.
            MatchResult otherMr = new MatchResult();
            otherMr.setId(999L);
            Individual ind = new Individual();
            ind.setId(INDIVIDUAL_ID);
            ind.setSpecies("Humpback whale");
            MatchCandidate stray = new MatchCandidate(otherMr, ind, 0.9, 1);
            stray.setId(CANDIDATE_ID);

            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);
            when(candidateRepo.findById(CANDIDATE_ID)).thenReturn(Optional.of(stray));

            assertThatThrownBy(() -> svc.accept(TASK_ID, new AcceptMatchRequest(CANDIDATE_ID, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong");
            verify(encounterRepo, never()).save(any());
        }

        @Test
        @DisplayName("species mismatch between encounter and individual → BusinessException")
        void speciesMismatch() {
            // Encounter species = Humpback, candidate's individual = Orca
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Orca");
            MatchCandidate cand = task.getResult().getCandidates().get(0);

            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);
            when(candidateRepo.findById(CANDIDATE_ID)).thenReturn(Optional.of(cand));

            assertThatThrownBy(() -> svc.accept(TASK_ID, new AcceptMatchRequest(CANDIDATE_ID, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Species mismatch");
            verify(encounterRepo, never()).save(any());
        }

        @Test
        @DisplayName("no project write access → ForbiddenException")
        void forbidden() {
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Humpback whale");
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(false);

            assertThatThrownBy(() -> svc.accept(TASK_ID, new AcceptMatchRequest(CANDIDATE_ID, null)))
                .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("task id unknown → NotFoundException")
        void taskNotFound() {
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.accept(TASK_ID, new AcceptMatchRequest(CANDIDATE_ID, null)))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // ---------- createIndividualFromQuery ----------

    @Nested
    @DisplayName("createIndividualFromQuery")
    class CreateIndividual {

        @Test
        @DisplayName("creates new Individual + assigns it + REJECTED_NEW_INDIVIDUAL")
        void happyPath() {
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Humpback whale");
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);
            when(individualRepo.save(any(Individual.class))).thenAnswer(inv -> {
                Individual i = inv.getArgument(0);
                i.setId(500L);
                return i;
            });

            CreateIndividualFromMatchRequest req =
                new CreateIndividualFromMatchRequest("Salt", "female", "new animal");
            IaTask result = svc.createIndividualFromQuery(TASK_ID, req);

            // New individual was saved with nickname + adopted species
            ArgumentCaptor<Individual> indCap = ArgumentCaptor.forClass(Individual.class);
            verify(individualRepo).save(indCap.capture());
            assertThat(indCap.getValue().getNickname()).isEqualTo("Salt");
            assertThat(indCap.getValue().getSex()).isEqualTo("female");
            assertThat(indCap.getValue().getSpecies()).isEqualTo("Humpback whale");

            // Encounter pointed at the new individual
            ArgumentCaptor<Encounter> encCap = ArgumentCaptor.forClass(Encounter.class);
            verify(encounterRepo).save(encCap.capture());
            assertThat(encCap.getValue().getIndividual().getId()).isEqualTo(500L);

            // MatchResult marked rejected-with-new
            MatchResult mr = task.getResult();
            assertThat(mr.getResolution()).isEqualTo(MatchResolution.REJECTED_NEW_INDIVIDUAL);
            assertThat(mr.getNewIndividualId()).isEqualTo(500L);
            assertThat(mr.getResolvedByUserId()).isEqualTo(CURRENT_USER_ID);
            assertThat(mr.getRemarks()).isEqualTo("new animal");

            verify(events, times(1)).publishEvent(any(EncounterChangedEvent.class));
            assertThat(result).isSameAs(task);
        }
    }

    // ---------- skip ----------

    @Nested
    @DisplayName("skip")
    class Skip {

        @Test
        @DisplayName("records resolution=SKIPPED without touching the encounter")
        void happyPath() {
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Humpback whale");
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);

            IaTask result = svc.skip(TASK_ID, new SkipMatchRequest("too blurry"));

            MatchResult mr = task.getResult();
            assertThat(mr.getResolution()).isEqualTo(MatchResolution.SKIPPED);
            assertThat(mr.getRemarks()).isEqualTo("too blurry");
            assertThat(mr.getResolvedByUserId()).isEqualTo(CURRENT_USER_ID);

            verify(encounterRepo, never()).save(any());          // encounter untouched
            verify(events, never()).publishEvent(any());          // no OS resync needed
            assertThat(result).isSameAs(task);
        }

        @Test
        @DisplayName("skip with no remarks body is accepted (remarks stays null)")
        void noRemarks() {
            IaTask task = doneTaskWithOneCandidate("Humpback whale", "Humpback whale");
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);

            svc.skip(TASK_ID, new SkipMatchRequest(null));

            assertThat(task.getResult().getResolution()).isEqualTo(MatchResolution.SKIPPED);
            assertThat(task.getResult().getRemarks()).isNull();
        }
    }

    // ---------- helpers ----------

    /**
     * Build a DONE IaTask with one MatchCandidate pointing at an
     * Individual whose species can be set differently from the
     * encounter's, so the mismatch test can drive the bad path.
     */
    private IaTask doneTaskWithOneCandidate(String encounterSpecies, String individualSpecies) {
        Encounter enc = new Encounter();
        enc.setId(ENCOUNTER_ID);
        enc.setProjectId(PROJECT_ID);
        enc.setSpecies(encounterSpecies);

        Annotation ann = new Annotation();
        ann.setId(10L);
        ann.setEncounter(enc);

        IaTask task = new IaTask();
        task.setId(TASK_ID);
        task.setAnnotation(ann);
        task.setStatus(IaTaskStatus.DONE);
        task.setAlgorithm("stub-v1");

        Individual ind = new Individual();
        ind.setId(INDIVIDUAL_ID);
        ind.setNickname("Salt");
        ind.setSpecies(individualSpecies);

        MatchResult mr = new MatchResult(task, "stub-v1");
        mr.setId(1L);
        mr.setTopScore(0.94);
        // Pre-build candidates list so the LIE-defense pre-touch in the
        // service can safely walk it.
        List<MatchCandidate> cands = new ArrayList<>();
        MatchCandidate c = new MatchCandidate(mr, ind, 0.94, 1);
        c.setId(CANDIDATE_ID);
        cands.add(c);
        mr.setCandidates(cands);
        task.setResult(mr);
        return task;
    }
}
