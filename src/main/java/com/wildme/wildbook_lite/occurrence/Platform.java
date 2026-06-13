package com.wildme.wildbook_lite.occurrence;

/**
 * Survey platform — how the observer was moving when they spotted the
 * group. Real Wildbook has this as a free-text dynamicProperty; we
 * pin it to an enum because it's a small fixed vocabulary and it lets
 * us index/filter cleanly.
 */
public enum Platform {
    BOAT,
    PLANE,
    FOOT,
    DRONE,
    VEHICLE,
    OTHER
}
