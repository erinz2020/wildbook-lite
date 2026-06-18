package com.wildme.wildbook_lite.organization;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Org-scope permission bean — exposed to SpEL in @PreAuthorize the
 * same way {@link com.wildme.wildbook_lite.project.ProjectGuard} is.
 *
 *   @PreAuthorize("@orgGuard.canRead(#orgId)")
 *   @PreAuthorize("@orgGuard.canManage(#orgId)")
 *
 * Naming aligns with ProjectGuard so a reader who knows one knows the
 * other:
 *   canRead    — any member (MEMBER or OWNER)
 *   canManage  — OWNER only (rename org, invite/kick members, create
 *                projects within the org)
 *
 * Why a bean (not a static helper):
 *  - SpEL `@beanName` references resolve against the application
 *    context — must be a managed bean.
 *  - Wraps DB access; needs transactions.
 *
 * Why no separate `canWrite`:
 *  - We collapsed the middle role: MEMBER reads, OWNER writes the
 *    org metadata. Project-level write happens at ProjectGuard.
 */
@Component("orgGuard")
public class OrgGuard {

    private final OrganizationMemberRepository memberRepository;

    public OrgGuard(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public boolean canRead(Long orgId) {
        return hasAtLeast(orgId, OrgRole.MEMBER);
    }

    @Transactional(readOnly = true)
    public boolean canManage(Long orgId) {
        return hasAtLeast(orgId, OrgRole.OWNER);
    }

    /**
     * Membership probe for a SPECIFIC user (not the caller). Used by
     * ProjectService when validating that the target of an
     * add-member call belongs to the project's org first.
     */
    @Transactional(readOnly = true)
    public boolean isMember(Long orgId, Long userId) {
        if (orgId == null || userId == null) return false;
        return memberRepository.findByOrgIdAndUserId(orgId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean hasAtLeast(Long orgId, OrgRole required) {
        Long userId = currentUserIdOrNull();
        if (userId == null || orgId == null) return false;
        return memberRepository.findByOrgIdAndUserId(orgId, userId)
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
