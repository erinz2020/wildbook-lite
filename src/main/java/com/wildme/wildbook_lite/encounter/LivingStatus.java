package com.wildme.wildbook_lite.encounter;

/**
 * Whether the animal was alive at the time of the encounter.
 *
 * Conservation programs care a lot about this — dead-stranding
 * records flow into different reporting pipelines than live-sighting
 * records. Keeping it as a typed enum (rather than a free string)
 * lets us drive that branching logic from typed code.
 */
public enum LivingStatus {
    ALIVE,
    DEAD,
    UNKNOWN
}
