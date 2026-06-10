package com.wildme.wildbook_lite.encounter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wildme.wildbook_lite.project.ProjectRole;

/**
 * Pure unit test on the transition table. No Spring, no DB.
 *
 * The state machine is the single source of truth for "what's allowed";
 * if anyone tweaks the table, this test pins down the contract.
 */
class EncounterStatusTest {

    @Test
    @DisplayName("DRAFT → REVIEWED requires EDITOR")
    void draftToReviewedNeedsEditor() {
        var t = EncounterStatus.find(EncounterStatus.DRAFT, EncounterStatus.REVIEWED);
        assertThat(t).isPresent();
        assertThat(t.get().minRole()).isEqualTo(ProjectRole.EDITOR);
    }

    @Test
    @DisplayName("REVIEWED → PUBLISHED requires OWNER")
    void reviewedToPublishedNeedsOwner() {
        var t = EncounterStatus.find(EncounterStatus.REVIEWED, EncounterStatus.PUBLISHED);
        assertThat(t).isPresent();
        assertThat(t.get().minRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    @DisplayName("PUBLISHED → DRAFT is NOT allowed (you must un-publish through REVIEWED)")
    void publishedToDraftDisallowed() {
        assertThat(EncounterStatus.find(EncounterStatus.PUBLISHED, EncounterStatus.DRAFT))
            .isEmpty();
    }

    @Test
    @DisplayName("same-state self-loop is rejected")
    void selfLoopRejected() {
        assertThat(EncounterStatus.find(EncounterStatus.DRAFT, EncounterStatus.DRAFT)).isEmpty();
        assertThat(EncounterStatus.find(EncounterStatus.PUBLISHED, EncounterStatus.PUBLISHED)).isEmpty();
    }

    @Test
    @DisplayName("nulls are rejected")
    void nullsRejected() {
        assertThat(EncounterStatus.find(null, EncounterStatus.DRAFT)).isEmpty();
        assertThat(EncounterStatus.find(EncounterStatus.DRAFT, null)).isEmpty();
        assertThat(EncounterStatus.find(null, null)).isEmpty();
    }

    @Test
    @DisplayName("ARCHIVED is a terminal state (nothing leaves it)")
    void archivedIsTerminal() {
        for (EncounterStatus target : EncounterStatus.values()) {
            assertThat(EncounterStatus.find(EncounterStatus.ARCHIVED, target))
                .as("ARCHIVED → %s should be disallowed", target)
                .isEmpty();
        }
    }

    @Test
    @DisplayName("every non-terminal state has at least one valid outgoing transition")
    void notStuck() {
        for (EncounterStatus from : EncounterStatus.values()) {
            if (from == EncounterStatus.ARCHIVED) continue;
            boolean hasOutgoing = false;
            for (EncounterStatus to : EncounterStatus.values()) {
                if (EncounterStatus.find(from, to).isPresent()) {
                    hasOutgoing = true;
                    break;
                }
            }
            assertThat(hasOutgoing)
                .as("%s should have at least one outgoing transition", from)
                .isTrue();
        }
    }
}
