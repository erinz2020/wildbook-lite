package com.wildme.wildbook_lite.project;

/**
 * Per-project role. Ordered by privilege so we can compare with .ordinal()
 * (higher ordinal = more permissions).
 */
public enum ProjectRole {
    VIEWER,  // read-only
    EDITOR,  // can write encounters/comments
    OWNER    // can manage members + delete project
}
