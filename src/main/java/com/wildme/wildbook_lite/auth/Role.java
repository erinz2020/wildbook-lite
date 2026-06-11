package com.wildme.wildbook_lite.auth;

/**
 * Global system roles. Project-level roles are separate (see ProjectRole).
 *
 *  - RESEARCHER : the default role for a regular user — can read /
 *                 contribute encounters within projects they are a
 *                 member of.
 *  - ADMIN      : platform admin — creates and disables users,
 *                 inspects audit log across every account.
 */
public enum Role {
    RESEARCHER,
    ADMIN
}
