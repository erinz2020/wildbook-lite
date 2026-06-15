package com.wildme.wildbook_lite.annotation;

/**
 * Which side / angle of the animal is visible in the annotation.
 * Critical for ID matching — most matchers only compare same-viewpoint
 * crops (a left-flank fluke isn't comparable to a right-flank fluke).
 *
 * Values mirror Wildbook IBEIS's standard set.
 */
public enum Viewpoint {
    LEFT,
    RIGHT,
    FRONT,
    BACK,
    TOP,
    BOTTOM,
    UPLEFT,
    UPRIGHT,
    DOWNLEFT,
    DOWNRIGHT,
    UNKNOWN
}
