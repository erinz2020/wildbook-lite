package com.wildme.wildbook_lite.project;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Component("projectGuard")
public class ProjectGuard {

    private final ProjectMemberRepository memberRepository;

    public ProjectGuard(ProjectMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
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

    private boolean hasAtLeast(Long projectId, ProjectRole required) {
        Long userId = currentUserIdOrNull();
        if (userId == null || projectId == null) return false;
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
