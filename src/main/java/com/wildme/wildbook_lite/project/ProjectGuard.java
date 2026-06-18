package com.wildme.wildbook_lite.project;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.organization.OrgGuard;

/**
 * Permission bean exposed to SpEL in @PreAuthorize.
 *
 *   @PreAuthorize("@projectGuard.canRead(#projectId)")
 *   @PreAuthorize("@projectGuard.canWrite(#projectId)")
 *   @PreAuthorize("@projectGuard.canManage(#projectId)")
 *
 * Why a bean (not a static helper):
 *  - SpEL inside @PreAuthorize resolves "@beanName" against the
 *    application context.
 *  - Transactions and DB access need a managed bean.
 *
 * Two-layer RBAC (new with the Organization feature):
 *
 *   Layer 1: ORG membership
 *     - If the project belongs to an org (project.organizationId != null),
 *       the caller MUST be a member of that org. No org membership →
 *       false, regardless of project-level role.
 *     - "Legacy" projects with no org fall back to layer 2 only.
 *
 *   Layer 2: PROJECT role
 *     - Caller must be a project_member of the project, with role
 *       >= the required threshold.
 *
 * Why @Lazy on OrgGuard:
 *   - OrgGuard isn't in our circular dependency path today, but a
 *     future org-side method might need to call ProjectGuard for the
 *     reverse direction ("project members of an org's projects"). The
 *     @Lazy is cheap insurance against that edge.
 */
@Component("projectGuard")
public class ProjectGuard {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final OrgGuard orgGuard;

    public ProjectGuard(ProjectRepository projectRepository,
                        ProjectMemberRepository memberRepository,
                        @Lazy OrgGuard orgGuard) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.orgGuard = orgGuard;
    }

    @Transactional(readOnly = true)
    public boolean canRead(Long projectId) {
        return hasAtLeast(projectId, ProjectRole.VIEWER);
    }

    @Transactional(readOnly = true)
    public boolean canWrite(Long projectId) {
        return hasAtLeast(projectId, ProjectRole.EDITOR);
    }

    @Transactional(readOnly = true)
    public boolean canManage(Long projectId) {
        return hasAtLeast(projectId, ProjectRole.OWNER);
    }

    /**
     * Membership probe for an *arbitrary* user (not the caller).
     * Used when validating that a target user belongs to the project
     * before we hand them work, e.g., encounter assignment.
     */
    @Transactional(readOnly = true)
    public boolean isMember(Long projectId, Long userId) {
        if (projectId == null || userId == null) return false;
        return memberRepository.findByProjectIdAndUserId(projectId, userId).isPresent();
    }

    /** Pulled out for callers that need to compare against a min role chosen at runtime (e.g., state-machine transitions). */
    @Transactional(readOnly = true)
    public boolean hasAtLeast(Long projectId, ProjectRole required) {
        Long userId = currentUserIdOrNull();
        if (userId == null || projectId == null) return false;

        // Layer 1: org gate (only when the project has an org).
        Long orgId = projectRepository.findById(projectId)
            .map(Project::getOrganizationId)
            .orElse(null);
        if (orgId != null && !orgGuard.isMember(orgId, userId)) {
            return false;
        }

        // Layer 2: project role threshold.
        return memberRepository.findByProjectIdAndUserId(projectId, userId)
            .map(m -> m.getRole().ordinal() >= required.ordinal())
            .orElse(false);
    }

    private Long currentUserIdOrNull() {
        try {
            return com.wildme.wildbook_lite.auth.SecurityUtils.currentUserId();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
