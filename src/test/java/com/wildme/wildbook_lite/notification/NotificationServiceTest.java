package com.wildme.wildbook_lite.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

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
import com.wildme.wildbook_lite.common.ForbiddenException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repo;
    @InjectMocks NotificationService svc;

    @BeforeEach
    void login() {
        AppPrincipal p = new AppPrincipal(42L, "alice", "x", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @AfterEach
    void logout() { SecurityContextHolder.clearContext(); }

    @Test
    @DisplayName("markAsRead refuses to mark someone else's notification")
    void markAsRead_forbiddenForOthers() {
        Notification other = new Notification(99L, Notification.Kind.ENCOUNTER_CREATED,
            "title", "body", "encounter", 1L);
        other.setId(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> svc.markAsRead(7L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("markAsRead flips the read flag and saves")
    void markAsRead_happyPath() {
        Notification mine = new Notification(42L, Notification.Kind.COMMENT_ADDED,
            "title", "body", "encounter", 1L);
        mine.setId(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(mine));
        when(repo.save(mine)).thenReturn(mine);

        Notification result = svc.markAsRead(7L);

        assertThat(result.isRead()).isTrue();
    }

    @Test
    @DisplayName("unreadCount queries by current userId")
    void unreadCount_byCurrentUser() {
        when(repo.countByRecipientUserIdAndRead(42L, false)).thenReturn(5L);

        assertThat(svc.unreadCount()).isEqualTo(5L);
    }
}
