package com.wildme.wildbook_lite.encounter;

import java.util.List;
import java.util.Optional;

import com.wildme.wildbook_lite.project.ProjectRole;

/**
 * Workflow status for an Encounter, plus a hand-rolled state machine.
 *
 *   DRAFT  ──(editor)──►  REVIEWED  ──(owner)──►  PUBLISHED
 *     │                      │                       │
 *     │                      └──(editor)─► DRAFT     │
 *     │                                              │
 *     └─────────(owner)────────► ARCHIVED ◄─(owner)──┘
 *
 * Why a lookup table instead of pulling in Spring Statemachine:
 *  - The whole machine is 6 transitions; an enum + List is the
 *    smallest thing that could possibly work.
 *  - Easy to read in a code review: scroll to the table, that's the
 *    entire spec.
 *  - Spring Statemachine is a great library, but it's a lot of
 *    machinery for this scale.
 *
 * Each transition pairs a (from, to) with the *minimum project role*
 * required to perform it. The service looks up the transition and
 * checks the caller's role.
 */
public enum EncounterStatus {

    DRAFT,
    REVIEWED,
    PUBLISHED,
    ARCHIVED;

    /** Bundles "what move is this" with "who's allowed to make it". */
    public record Transition(EncounterStatus from, EncounterStatus to, ProjectRole minRole) {}

    private static final List<Transition> ALLOWED = List.of(
        // promote a draft to review (any editor)
        new Transition(DRAFT,     REVIEWED,  ProjectRole.EDITOR),
        // bounce it back to the author for changes
        new Transition(REVIEWED,  DRAFT,     ProjectRole.EDITOR),
        // owners gate publication
        new Transition(REVIEWED,  PUBLISHED, ProjectRole.OWNER),
        // un-publish (e.g., correction)
        new Transition(PUBLISHED, REVIEWED,  ProjectRole.OWNER),
        // archive from any state — only the owner
        new Transition(DRAFT,     ARCHIVED,  ProjectRole.OWNER),
        new Transition(REVIEWED,  ARCHIVED,  ProjectRole.OWNER),
        new Transition(PUBLISHED, ARCHIVED,  ProjectRole.OWNER)
    );

    public static Optional<Transition> find(EncounterStatus from, EncounterStatus to) {
        if (from == null || to == null || from == to) return Optional.empty();
        return ALLOWED.stream()
            .filter(t -> t.from() == from && t.to() == to)
            .findFirst();
    }
}
