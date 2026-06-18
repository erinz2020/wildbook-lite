package com.wildme.wildbook_lite.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wildme.wildbook_lite.auth.User;
import com.wildme.wildbook_lite.auth.UserRepository;
import com.wildme.wildbook_lite.notification.EncounterAssignedEvent;
import com.wildme.wildbook_lite.notification.EncounterCreatedEvent;
import com.wildme.wildbook_lite.notification.EncounterPublishedEvent;
import com.wildme.wildbook_lite.project.ProjectMember;
import com.wildme.wildbook_lite.project.ProjectMemberRepository;
import com.wildme.wildbook_lite.project.ProjectRole;

/**
 * Unit tests for {@link EmailListener} — verifies the recipient
 * resolution logic, the exclude-actor rule, and the opt-in filter.
 *
 * AOP scope limits (same as EmailSenderTest):
 *   @TransactionalEventListener + @Async are Spring proxies and don't
 *   fire on raw object calls. These tests invoke the listener methods
 *   directly — that's the right boundary: we're testing the recipient-
 *   selection logic, NOT Spring's event dispatch machinery.
 */
@ExtendWith(MockitoExtension.class)
class EmailListenerTest {

    @Mock ProjectMemberRepository memberRepo;
    @Mock UserRepository userRepo;
    @Mock EmailSender emailSender;

    @InjectMocks
    EmailListener listener;

    private static final Long PROJECT_ID = 7L;
    private static final Long ENCOUNTER_ID = 42L;
    private static final Long ACTOR = 99L;

    // ---------- onEncounterCreated ----------

    @Nested
    @DisplayName("onEncounterCreated")
    class Created {

        @Test
        @DisplayName("emails every opted-in project member except the actor")
        void fanout() {
            // Project has 4 members: actor (99), alice (1), bob (2), carol (3)
            when(memberRepo.findByProjectId(PROJECT_ID)).thenReturn(List.of(
                member(ACTOR, ProjectRole.OWNER),
                member(1L, ProjectRole.EDITOR),
                member(2L, ProjectRole.VIEWER),
                member(3L, ProjectRole.VIEWER)
            ));
            // Three non-actor users — alice opted-in, bob opted-out, carol no email
            when(userRepo.findAllById(anyCollection())).thenReturn(List.of(
                user(1L, "alice@x.com", true),
                user(2L, "bob@x.com", false),    // opted out → skipped
                user(3L, null, true)              // no email → skipped
            ));

            listener.onEncounterCreated(new EncounterCreatedEvent(
                ENCOUNTER_ID, PROJECT_ID, ACTOR, Instant.now()));

            // Only alice gets the email.
            ArgumentCaptor<EmailMessage> cap = ArgumentCaptor.forClass(EmailMessage.class);
            verify(emailSender, times(1)).send(cap.capture());
            assertThat(cap.getValue().to()).isEqualTo("alice@x.com");
        }

        @Test
        @DisplayName("no opted-in recipients → no emails sent")
        void noOptInRecipients() {
            when(memberRepo.findByProjectId(PROJECT_ID)).thenReturn(List.of(
                member(ACTOR, ProjectRole.OWNER),
                member(1L, ProjectRole.VIEWER)
            ));
            when(userRepo.findAllById(anyCollection())).thenReturn(List.of(
                user(1L, "alice@x.com", false)    // opted out
            ));

            listener.onEncounterCreated(new EncounterCreatedEvent(
                ENCOUNTER_ID, PROJECT_ID, ACTOR, Instant.now()));

            verify(emailSender, never()).send(any());
        }

        @Test
        @DisplayName("solo project (only the actor) → no recipients lookup, no emails")
        void onlyActor() {
            when(memberRepo.findByProjectId(PROJECT_ID)).thenReturn(List.of(
                member(ACTOR, ProjectRole.OWNER)
            ));

            listener.onEncounterCreated(new EncounterCreatedEvent(
                ENCOUNTER_ID, PROJECT_ID, ACTOR, Instant.now()));

            // The "no recipients" fast path: don't even hit the user repo.
            verify(userRepo, never()).findAllById(anyCollection());
            verify(emailSender, never()).send(any());
        }
    }

    // ---------- onEncounterAssigned ----------

    @Nested
    @DisplayName("onEncounterAssigned")
    class Assigned {

        @Test
        @DisplayName("emails the assignee directly when they're opted in")
        void happyPath() {
            User bob = user(5L, "bob@x.com", true);
            when(userRepo.findById(5L)).thenReturn(Optional.of(bob));

            listener.onEncounterAssigned(new EncounterAssignedEvent(
                ENCOUNTER_ID, PROJECT_ID, 5L /*assignee*/, ACTOR, Instant.now()));

            ArgumentCaptor<EmailMessage> cap = ArgumentCaptor.forClass(EmailMessage.class);
            verify(emailSender).send(cap.capture());
            assertThat(cap.getValue().to()).isEqualTo("bob@x.com");
            assertThat(cap.getValue().subject()).contains("assigned to you");
        }

        @Test
        @DisplayName("assignee opted out → no email")
        void optedOut() {
            User bob = user(5L, "bob@x.com", false);
            when(userRepo.findById(5L)).thenReturn(Optional.of(bob));

            listener.onEncounterAssigned(new EncounterAssignedEvent(
                ENCOUNTER_ID, PROJECT_ID, 5L, ACTOR, Instant.now()));

            verify(emailSender, never()).send(any());
        }

        @Test
        @DisplayName("assignee not found → no email")
        void assigneeMissing() {
            when(userRepo.findById(5L)).thenReturn(Optional.empty());

            listener.onEncounterAssigned(new EncounterAssignedEvent(
                ENCOUNTER_ID, PROJECT_ID, 5L, ACTOR, Instant.now()));

            verify(emailSender, never()).send(any());
        }
    }

    // ---------- onEncounterPublished ----------

    @Nested
    @DisplayName("onEncounterPublished")
    class Published {

        @Test
        @DisplayName("publish fanout: same exclude-actor + opt-in rules as creation")
        void fanout() {
            when(memberRepo.findByProjectId(PROJECT_ID)).thenReturn(List.of(
                member(ACTOR, ProjectRole.OWNER),
                member(1L, ProjectRole.EDITOR)
            ));
            when(userRepo.findAllById(anyCollection())).thenReturn(List.of(
                user(1L, "alice@x.com", true)
            ));

            listener.onEncounterPublished(new EncounterPublishedEvent(
                ENCOUNTER_ID, PROJECT_ID, "Humpback whale", ACTOR, Instant.now()));

            ArgumentCaptor<EmailMessage> cap = ArgumentCaptor.forClass(EmailMessage.class);
            verify(emailSender, times(1)).send(cap.capture());
            assertThat(cap.getValue().body()).contains("Humpback whale");
        }
    }

    // ---------- helpers ----------

    private ProjectMember member(Long userId, ProjectRole role) {
        return new ProjectMember(PROJECT_ID, userId, role);
    }

    private User user(Long id, String email, boolean optIn) {
        User u = new User("user" + id, email, "HASH");
        u.setId(id);
        u.setEmailOptIn(optIn);
        return u;
    }
}
