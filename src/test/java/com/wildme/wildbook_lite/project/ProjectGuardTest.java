package com.wildme.wildbook_lite.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wildme.wildbook_lite.auth.AppPrincipal;
import com.wildme.wildbook_lite.organization.OrgGuard;

/**
 * Mockito + JUnit 5 — classic service test.
 *
 *  - @ExtendWith(MockitoExtension.class) wires @Mock / @InjectMocks.
 *  - @Mock creates a mock of the dependency.
 *  - @InjectMocks creates the SUT and injects the mocks via constructor.
 *  - when(...).thenReturn(...) stubs behavior.
 *
 * Plus a tiny SecurityContext setup helper to simulate "the request
 * comes in with this user logged in". We tear it down after each test
 * so the context doesn't leak between tests (a classic flaky-test cause).
 *
 * Org layer notes: every existing test exercises legacy projects (no
 * org set), so ProjectGuard's org gate short-circuits early. The new
 * org layering has its own dedicated tests below in `OrgLayer`.
 */
@ExtendWith(MockitoExtension.class)
class ProjectGuardTest {

    @Mock
    private ProjectMemberRepository memberRepo;

    @Mock
    private ProjectRepository projectRepo;

    @Mock
    private OrgGuard orgGuard;

    @InjectMocks
    private ProjectGuard guard;

    @BeforeEach
    void login() {
        AppPrincipal principal = new AppPrincipal(
            42L, "alice", "ignored", true, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("canRead returns true for VIEWER on the project")
    void viewerCanRead() {
        ProjectMember member = new ProjectMember(7L, 42L, ProjectRole.VIEWER);
        when(memberRepo.findByProjectIdAndUserId(7L, 42L)).thenReturn(Optional.of(member));

        assertThat(guard.canRead(7L)).isTrue();
        assertThat(guard.canWrite(7L)).isFalse();
        assertThat(guard.canManage(7L)).isFalse();
    }

    @Test
    @DisplayName("OWNER passes every check")
    void ownerHasAll() {
        ProjectMember member = new ProjectMember(7L, 42L, ProjectRole.OWNER);
        when(memberRepo.findByProjectIdAndUserId(7L, 42L)).thenReturn(Optional.of(member));

        assertThat(guard.canRead(7L)).isTrue();
        assertThat(guard.canWrite(7L)).isTrue();
        assertThat(guard.canManage(7L)).isTrue();
    }

    @Test
    @DisplayName("non-member is denied everything")
    void nonMemberDenied() {
        when(memberRepo.findByProjectIdAndUserId(99L, 42L)).thenReturn(Optional.empty());

        assertThat(guard.canRead(99L)).isFalse();
        assertThat(guard.canWrite(99L)).isFalse();
        assertThat(guard.canManage(99L)).isFalse();
    }

    @Test
    @DisplayName("anonymous (no SecurityContext) is denied without hitting the repo")
    void anonymousDenied() {
        SecurityContextHolder.clearContext();

        assertThat(guard.canRead(7L)).isFalse();
        // Mockito would have failed verification if the repo was called.
    }

    // ===== Org-layer RBAC (added with Option D) =====

    @org.junit.jupiter.api.Nested
    @DisplayName("Org-layer gate")
    class OrgLayer {

        @Test
        @DisplayName("project has an org and caller is NOT a member of the org → denied even with project role")
        void deniedWhenNotInOrg() {
            Project p = new Project("P", "d", 99L);
            p.setOrganizationId(50L);
            org.mockito.Mockito.when(projectRepo.findById(7L)).thenReturn(java.util.Optional.of(p));
            // Project membership says OWNER, but the org gate slams it shut.
            org.mockito.Mockito.when(orgGuard.isMember(50L, 42L)).thenReturn(false);

            assertThat(guard.canRead(7L)).isFalse();
            assertThat(guard.canManage(7L)).isFalse();
            // Project member lookup never even runs — fast denial.
            org.mockito.Mockito.verify(memberRepo, org.mockito.Mockito.never())
                .findByProjectIdAndUserId(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong());
        }

        @Test
        @DisplayName("project has an org, caller is an org member: project-role threshold then applies")
        void orgMemberThenProjectRole() {
            Project p = new Project("P", "d", 99L);
            p.setOrganizationId(50L);
            org.mockito.Mockito.when(projectRepo.findById(7L)).thenReturn(java.util.Optional.of(p));
            org.mockito.Mockito.when(orgGuard.isMember(50L, 42L)).thenReturn(true);
            org.mockito.Mockito.when(memberRepo.findByProjectIdAndUserId(7L, 42L))
                .thenReturn(java.util.Optional.of(new ProjectMember(7L, 42L, ProjectRole.EDITOR)));

            assertThat(guard.canRead(7L)).isTrue();
            assertThat(guard.canWrite(7L)).isTrue();
            assertThat(guard.canManage(7L)).isFalse(); // EDITOR < OWNER
        }

        @Test
        @DisplayName("legacy project (organizationId=null) bypasses the org gate entirely")
        void legacyProjectStillWorks() {
            Project p = new Project("P", "d", 99L);
            // organizationId left null on purpose
            org.mockito.Mockito.when(projectRepo.findById(7L)).thenReturn(java.util.Optional.of(p));
            org.mockito.Mockito.when(memberRepo.findByProjectIdAndUserId(7L, 42L))
                .thenReturn(java.util.Optional.of(new ProjectMember(7L, 42L, ProjectRole.OWNER)));

            assertThat(guard.canManage(7L)).isTrue();
            // OrgGuard never consulted for a null-org project.
            org.mockito.Mockito.verify(orgGuard, org.mockito.Mockito.never())
                .isMember(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong());
        }
    }
}
