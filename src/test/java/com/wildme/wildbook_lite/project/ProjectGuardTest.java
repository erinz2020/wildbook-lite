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
 */
@ExtendWith(MockitoExtension.class)
class ProjectGuardTest {

    @Mock
    private ProjectMemberRepository memberRepo;

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
}
