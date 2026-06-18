package com.wildme.wildbook_lite.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wildme.wildbook_lite.notification.EncounterAssignedEvent;
import com.wildme.wildbook_lite.notification.EncounterCreatedEvent;
import com.wildme.wildbook_lite.notification.EncounterPublishedEvent;

/**
 * Pure unit tests for the template builders — no Spring, no mocks.
 * Verifies that every event's id / project / actor land in the body
 * and the subject is the right phrase. These tests are the canary for
 * accidental template breakage.
 */
class EmailTemplatesTest {

    @Test
    @DisplayName("encounterCreated builds subject + body with all key fields")
    void encounterCreated() {
        EncounterCreatedEvent e = new EncounterCreatedEvent(
            42L, 7L, 99L, Instant.parse("2026-06-11T08:30:00Z"));

        EmailMessage msg = EmailTemplates.encounterCreated("alice@example.com", e);

        assertThat(msg.to()).isEqualTo("alice@example.com");
        assertThat(msg.subject()).contains("New encounter");
        assertThat(msg.body())
            .contains("Encounter ID: 42")
            .contains("Project ID:   7")
            .contains("Recorded by:  user 99")
            .contains("/encounters/42")
            .contains("emailOptIn");   // unsubscribe hint
    }

    @Test
    @DisplayName("encounterAssigned references the assignee + assigner")
    void encounterAssigned() {
        EncounterAssignedEvent e = new EncounterAssignedEvent(
            42L, 7L, 5L /*assignee*/, 99L /*assigner*/, Instant.now());

        EmailMessage msg = EmailTemplates.encounterAssigned("bob@example.com", e);

        assertThat(msg.to()).isEqualTo("bob@example.com");
        assertThat(msg.subject()).contains("assigned to you");
        assertThat(msg.body())
            .contains("Encounter ID: 42")
            .contains("Assigned by:  user 99")
            .contains("/encounters/42");
    }

    @Test
    @DisplayName("encounterPublished includes species in the body")
    void encounterPublished() {
        EncounterPublishedEvent e = new EncounterPublishedEvent(
            42L, 7L, "Humpback whale", 99L, Instant.now());

        EmailMessage msg = EmailTemplates.encounterPublished("carol@example.com", e);

        assertThat(msg.subject()).contains("published");
        assertThat(msg.body())
            .contains("Species:      Humpback whale")
            .contains("Published by: user 99");
    }

    @Test
    @DisplayName("encounterPublished falls back to 'unknown' when species is null")
    void encounterPublishedNullSpecies() {
        EncounterPublishedEvent e = new EncounterPublishedEvent(
            42L, 7L, null, 99L, Instant.now());

        EmailMessage msg = EmailTemplates.encounterPublished("carol@example.com", e);

        assertThat(msg.body()).contains("Species:      unknown");
    }
}
