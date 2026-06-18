package com.wildme.wildbook_lite.organization;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.organization.dto.AddOrgMemberRequest;
import com.wildme.wildbook_lite.organization.dto.CreateOrganizationRequest;
import com.wildme.wildbook_lite.organization.dto.OrganizationResponse;
import com.wildme.wildbook_lite.organization.dto.UpdateOrganizationRequest;

import jakarta.validation.Valid;

/**
 * REST surface for the Organization aggregate.
 *
 *   POST   /api/organizations                       — any authenticated user (creator becomes OWNER)
 *   GET    /api/organizations                       — my orgs (membership-driven)
 *   GET    /api/organizations/{id}                  — must be member
 *   PATCH  /api/organizations/{id}                  — owner only
 *   DELETE /api/organizations/{id}                  — owner only; refuses if has projects
 *
 *   GET    /api/organizations/{id}/members          — must be member
 *   POST   /api/organizations/{id}/members          — owner only (upsert)
 *   DELETE /api/organizations/{id}/members/{userId} — owner only OR self-leave
 *
 * Why a single POST for add+update vs separate POST/PATCH:
 *   - "Grant role X to user U on org O" is one idea. Splitting it into
 *     "add" vs "update" forces clients to know whether the row exists,
 *     which is internal state we don't want to leak. Upsert is the
 *     common-case-correct shape.
 */
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService service;
    private final OrganizationMemberRepository memberRepo;

    public OrganizationController(OrganizationService service,
                                  OrganizationMemberRepository memberRepo) {
        this.service = service;
        this.memberRepo = memberRepo;
    }

    @PostMapping
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest req) {
        Organization saved = service.create(req);
        return OrganizationResponse.from(saved, OrgRole.OWNER);
    }

    @GetMapping
    public List<OrganizationResponse> listMyOrgs() {
        Long userId = SecurityUtils.currentUserId();
        return service.listMyOrgs().stream()
            .map(o -> OrganizationResponse.from(o, lookupRole(o.getId(), userId)))
            .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@orgGuard.canRead(#id)")
    public OrganizationResponse get(@PathVariable Long id) {
        Long userId = SecurityUtils.currentUserId();
        return OrganizationResponse.from(service.findById(id), lookupRole(id, userId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@orgGuard.canManage(#id)")
    public OrganizationResponse update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateOrganizationRequest req) {
        Organization updated = service.update(id, req);
        return OrganizationResponse.from(updated, OrgRole.OWNER);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@orgGuard.canManage(#id)")
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@orgGuard.canRead(#id)")
    public List<OrganizationMember> listMembers(@PathVariable Long id) {
        return service.listMembers(id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@orgGuard.canManage(#id)")
    public OrganizationMember addOrUpdateMember(@PathVariable Long id,
                                                @Valid @RequestBody AddOrgMemberRequest req) {
        return service.addOrUpdateMember(id, req);
    }

    /**
     * Owner can remove anyone; a non-owner may only remove THEMSELVES
     * (self-leave). That mixed authorization can't live cleanly in a
     * single @PreAuthorize SpEL, so we do the check in the body and
     * fall back to canRead at the gate (any member can even reach this).
     */
    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@orgGuard.canRead(#id)")
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        Long caller = SecurityUtils.currentUserId();
        boolean callerIsOwner = memberRepo.findByOrgIdAndUserId(id, caller)
            .map(m -> m.getRole() == OrgRole.OWNER)
            .orElse(false);
        if (!callerIsOwner && !caller.equals(userId)) {
            throw new com.wildme.wildbook_lite.common.ForbiddenException(
                "Only org OWNERs can remove other members. You can remove yourself.");
        }
        service.removeMember(id, userId);
    }

    // ----- helpers -----

    private OrgRole lookupRole(Long orgId, Long userId) {
        return memberRepo.findByOrgIdAndUserId(orgId, userId)
            .map(OrganizationMember::getRole)
            .orElse(null);
    }
}
