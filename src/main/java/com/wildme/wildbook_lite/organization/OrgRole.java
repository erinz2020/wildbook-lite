package com.wildme.wildbook_lite.organization;

/**
 * Organization-level role. Deliberately smaller than ProjectRole:
 *
 *  - OWNER  — can manage the org (rename, delete, invite/kick members,
 *             promote/demote, create projects within it).
 *  - MEMBER — can see the org and its projects (subject to project-level
 *             roles on each individual project).
 *
 * Why only two roles (vs ProjectRole's three): an org is administrative
 * scaffolding. The interesting access-control granularity lives at the
 * project level. Adding a third org role would be design-by-symmetry —
 * not driven by a real use case we can name.
 *
 * Ordering matters: OWNER.ordinal() > MEMBER.ordinal(). OrgGuard's
 * `hasAtLeast` uses this same trick that ProjectGuard does.
 */
public enum OrgRole {
    MEMBER,
    OWNER
}
