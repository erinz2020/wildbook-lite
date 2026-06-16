package com.wildme.wildbook_lite.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wildme.wildbook_lite.annotation.Annotation;
import com.wildme.wildbook_lite.annotation.AnnotationRepository;
import com.wildme.wildbook_lite.auth.AppPrincipal;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.project.ProjectGuard;

/**
 * Unit tests for {@link IaTaskService} — the synchronous orchestration
 * around the async IA pipeline. The runner is a mock (we're not
 * testing the @Async behavior here, just that enqueue dispatches to it).
 *
 * Important Spring Boot point reinforced by these tests:
 *   - enqueue() saves the task FIRST, then calls runner.run(savedId).
 *     Passing the ID (not the entity) across the @Async boundary keeps
 *     the runner's persistence context independent — verified by the
 *     "enqueue dispatches the saved id" test below.
 */
@ExtendWith(MockitoExtension.class)
class IaTaskServiceTest {

    @Mock IaTaskRepository taskRepo;
    @Mock AnnotationRepository annotationRepo;
    @Mock IaTaskRunner runner;
    @Mock ProjectGuard projectGuard;

    @InjectMocks
    IaTaskService svc;

    private static final Long ANNOTATION_ID = 10L;
    private static final Long ENCOUNTER_ID  = 100L;
    private static final Long PROJECT_ID    = 1L;
    private static final Long TASK_ID       = 42L;
    private static final Long CURRENT_USER_ID = 99L;

    @BeforeEach
    void login() {
        AppPrincipal principal = new AppPrincipal(
            CURRENT_USER_ID, "alice", "ignored", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    // ---------- enqueue ----------

    @Nested
    @DisplayName("enqueue")
    class Enqueue {

        @Test
        @DisplayName("happy path: saves PENDING task with submitter + dispatches runner with the saved id")
        void happyPath() {
            Annotation a = annotation();
            when(annotationRepo.findById(ANNOTATION_ID)).thenReturn(Optional.of(a));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);
            when(taskRepo.save(any(IaTask.class))).thenAnswer(inv -> {
                IaTask t = inv.getArgument(0);
                t.setId(TASK_ID);
                return t;
            });

            IaTask saved = svc.enqueue(ANNOTATION_ID);

            assertThat(saved.getId()).isEqualTo(TASK_ID);
            assertThat(saved.getStatus()).isEqualTo(IaTaskStatus.PENDING);
            assertThat(saved.getSubmitterUserId()).isEqualTo(CURRENT_USER_ID);
            assertThat(saved.getAnnotation()).isSameAs(a);
            // Runner dispatched with the SAVED id (not the entity).
            verify(runner, times(1)).run(TASK_ID);
        }

        @Test
        @DisplayName("annotation not found → NotFoundException (no save, no dispatch)")
        void annotationMissing() {
            when(annotationRepo.findById(ANNOTATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> svc.enqueue(ANNOTATION_ID))
                .isInstanceOf(NotFoundException.class);
            verify(taskRepo, never()).save(any());
            verify(runner, never()).run(any());
        }

        @Test
        @DisplayName("no project write access → ForbiddenException (no save, no dispatch)")
        void forbidden() {
            when(annotationRepo.findById(ANNOTATION_ID)).thenReturn(Optional.of(annotation()));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(false);

            assertThatThrownBy(() -> svc.enqueue(ANNOTATION_ID))
                .isInstanceOf(ForbiddenException.class);
            verify(taskRepo, never()).save(any());
            verify(runner, never()).run(any());
        }

        @Test
        @DisplayName("annotation without encounter (orphan) still enqueues — no project gate to enforce")
        void orphanAnnotation() {
            Annotation orphan = new Annotation();
            orphan.setId(ANNOTATION_ID);
            // No encounter set — projectId resolves to null, guard skipped.
            when(annotationRepo.findById(ANNOTATION_ID)).thenReturn(Optional.of(orphan));
            when(taskRepo.save(any(IaTask.class))).thenAnswer(inv -> {
                IaTask t = inv.getArgument(0);
                t.setId(TASK_ID);
                return t;
            });

            IaTask saved = svc.enqueue(ANNOTATION_ID);

            assertThat(saved.getId()).isEqualTo(TASK_ID);
            verify(projectGuard, never()).canWrite(any());  // no project to gate on
            verify(runner, times(1)).run(TASK_ID);
        }
    }

    // ---------- cancel ----------

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        @DisplayName("PENDING → CANCELLED, endedAt stamped")
        void cancelsPending() {
            IaTask task = newPendingTask();
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);
            when(taskRepo.save(any(IaTask.class))).thenAnswer(inv -> inv.getArgument(0));

            IaTask after = svc.cancel(TASK_ID);

            assertThat(after.getStatus()).isEqualTo(IaTaskStatus.CANCELLED);
            assertThat(after.getEndedAt()).isNotNull();
        }

        @Test
        @DisplayName("non-PENDING status (RUNNING) → BusinessException, no save")
        void rejectsRunning() {
            IaTask task = newPendingTask();
            task.setStatus(IaTaskStatus.RUNNING);
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);

            assertThatThrownBy(() -> svc.cancel(TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only PENDING");
            verify(taskRepo, never()).save(any());
        }

        @Test
        @DisplayName("non-PENDING status (DONE) → BusinessException")
        void rejectsDone() {
            IaTask task = newPendingTask();
            task.setStatus(IaTaskStatus.DONE);
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);

            assertThatThrownBy(() -> svc.cancel(TASK_ID))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("no write access → ForbiddenException")
        void forbidden() {
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(newPendingTask()));
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(false);

            assertThatThrownBy(() -> svc.cancel(TASK_ID))
                .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("task not found → NotFoundException")
        void taskNotFound() {
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> svc.cancel(TASK_ID))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // ---------- findById ----------

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns task with read access, pre-touch doesn't blow up on empty result")
        void happyPathNoResult() {
            IaTask task = newPendingTask();    // status PENDING, result = null
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);

            IaTask out = svc.findById(TASK_ID);

            assertThat(out).isSameAs(task);
            // Sanity: the pre-touch loop tolerates result == null
            assertThat(out.getResult()).isNull();
        }

        @Test
        @DisplayName("no read access → ForbiddenException")
        void forbidden() {
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(newPendingTask()));
            when(projectGuard.canRead(PROJECT_ID)).thenReturn(false);

            assertThatThrownBy(() -> svc.findById(TASK_ID))
                .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("task not found → NotFoundException")
        void notFound() {
            when(taskRepo.findById(TASK_ID)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.findById(TASK_ID))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // ---------- listByAnnotation ----------

    @Nested
    @DisplayName("listByAnnotation")
    class ListByAnnotation {

        @Test
        @DisplayName("delegates to repo with the right ordering + filter")
        void happyPath() {
            Annotation a = annotation();
            Pageable pg = PageRequest.of(0, 20);
            IaTask one = newPendingTask();
            Page<IaTask> page = new PageImpl<>(List.of(one), pg, 1);

            when(annotationRepo.findById(ANNOTATION_ID)).thenReturn(Optional.of(a));
            when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);
            when(taskRepo.findByAnnotationIdOrderByCreatedAtDesc(ANNOTATION_ID, pg)).thenReturn(page);

            Page<IaTask> out = svc.listByAnnotation(ANNOTATION_ID, pg);

            assertThat(out.getTotalElements()).isEqualTo(1);
            assertThat(out.getContent()).containsExactly(one);
        }

        @Test
        @DisplayName("no read access → ForbiddenException")
        void forbidden() {
            Annotation a = annotation();
            when(annotationRepo.findById(ANNOTATION_ID)).thenReturn(Optional.of(a));
            when(projectGuard.canRead(PROJECT_ID)).thenReturn(false);

            assertThatThrownBy(() -> svc.listByAnnotation(ANNOTATION_ID, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    // ---------- helpers ----------

    private Annotation annotation() {
        Encounter enc = new Encounter();
        enc.setId(ENCOUNTER_ID);
        enc.setProjectId(PROJECT_ID);
        enc.setSpecies("Humpback whale");
        Annotation a = new Annotation();
        a.setId(ANNOTATION_ID);
        a.setEncounter(enc);
        return a;
    }

    private IaTask newPendingTask() {
        IaTask t = new IaTask();
        t.setId(TASK_ID);
        t.setAnnotation(annotation());
        t.setStatus(IaTaskStatus.PENDING);
        t.setAlgorithm("stub-v1");
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }
}
