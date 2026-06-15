package com.wildme.wildbook_lite.ml;

/**
 * Human-review outcome on a MatchResult.
 *
 *   PENDING                 → DONE task, no reviewer decision yet
 *   ACCEPTED                → reviewer picked one of the candidates;
 *                             encounter.individual was set to that candidate
 *   REJECTED_NEW_INDIVIDUAL → none of the candidates matched, reviewer
 *                             created a brand-new Individual; encounter
 *                             points at it
 *   SKIPPED                 → reviewer explicitly deferred / declined
 *                             to decide; encounter unchanged
 *
 * Why an enum vs a boolean accepted/rejected flag:
 *   - "not yet reviewed" and "reviewed and declined" are different
 *     states with different downstream behavior (filters, queues).
 *     A boolean can't carry that.
 *   - SKIPPED preserves the audit fact that a human looked at it.
 */
public enum MatchResolution {
    PENDING,
    ACCEPTED,
    REJECTED_NEW_INDIVIDUAL,
    SKIPPED
}
