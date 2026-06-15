package com.wildme.wildbook_lite.ml;

/**
 * Lifecycle of an identification (IA) ML job.
 *
 *   PENDING   → just enqueued, runner hasn't picked it up yet
 *   RUNNING   → executor thread has started the actual work
 *   DONE      → terminal success, MatchResult populated
 *   FAILED    → terminal failure, errorMessage populated
 *   CANCELLED → user-initiated cancel; only valid from PENDING
 *
 * Status changes go through IaTaskService so the state-machine rules
 * stay in one place. We don't use the EncounterStatus-style lookup
 * table here because there are only ~5 states and 3 legal arrows —
 * the lookup table machinery would be overkill.
 */
public enum IaTaskStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED
}
