package com.wildme.wildbook_lite.organization;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.organization.dto.AddOrgMemberRequest;
import com.wildme.wildbook_lite.organization.dto.CreateOrganizationRequest;
import com.wildme.wildbook_lite.organization.dto.UpdateOrganizationRequest;
import com.wildme.wildbook_lite.project.Project;
import com.wildme.wildbook_lite.project.ProjectRepository;

/**
 * Owner of the Organization aggregate.
 *
 * Invariants enforced:
 *   - An org ALWAYS has at least one OWNER. We refuse any operation
 *     (demote, remove, leave) that would leave it ownerless. This is
 *     the "last-owner protection" pattern — same as GitHub orgs.
 *   - Slug is globally UNIQUE (case-insensitive). Re-derive from
 *     `name` if the client doesn't supply one; reject on conflict.
 *   - An org CANNOT be deleted while it still has projects. Caller
 *     must delete or re-home the projects first. (Future: cascade
 *     deletion of org + all projects, gated behind a separate
 *     `?force=true` endpoint with its own audit.)
 *
 * What goes through OrgGuard vs what is checked inline here:
 *   - Controllers use @PreAuthorize("@orgGuard.canManage(...)") for
 *     the coarse "are you allowed to call this at all" gate.
 *   - This service layer also enforces business invariants the
 *     guard doesn't know about (slug uniqueness, last-owner, has-projects).
 *
 * Why we keep `Organization.ownerUserId` in sync with the OWNER role
 * row: it's a denormalized "first owner / creator" field that drives
 * "orgs I created" listings without a join. The OWNER role itself
 * remains authoritative for permissions; the column is just a cache.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository orgRepo;
    private final OrganizationMemberRepository memberRepo;
    private final ProjectRepository projectRepo;

    public OrganizationService(OrganizationRepository orgRepo,
                               OrganizationMemberRepository memberRepo,
                               ProjectRepository projectRepo) {
        this.orgRepo = orgRepo;
        this.memberRepo = memberRepo;
        this.projectRepo = projectRepo;
    }

    @Audited("organization.create")
    @Transactional
    public Organization create(CreateOrganizationRequest req) {
        Long currentUserId = SecurityUtils.currentUserId();
        String slug = (req.slug() != null && !req.slug().isBlank())
            ? req.slug().trim()
            : slugify(req.name());

        orgRepo.findBySlugIgnoreCase(slug).ifPresent(existing -> {
            throw new BusinessException("Organization slug already in use: " + slug);
        });

        Organization saved = orgRepo.save(new Organization(
            req.name().trim(), slug, req.description(), currentUserId));

        // Bootstrap: creator becomes the OWNER.
        memberRepo.save(new OrganizationMember(saved.getId(), currentUserId, OrgRole.OWNER));
        return saved;
    }

    @Transactional(readOnly = true)
    public Organization findById(Long id) {
        return orgRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Organization not found: " + id));
    }

    /** "Orgs I'm a member of" — driven by the user_id-indexed OrganizationMember row. */
    @Transactional(readOnly = true)
    public List<Organization> listMyOrgs() {
        Long userId = SecurityUtils.currentUserId();
        List<Long> orgIds = memberRepo.findByUserId(userId).stream()
            .map(OrganizationMember::getOrgId)
            .toList();
        if (orgIds.isEmpty()) return List.of();
        return orgRepo.findAllById(orgIds);
    }

    @Audited("organization.update")
    @Transactional
    public Organization update(Long id, UpdateOrganizationRequest req) {
        Organization org = findById(id);

        if (req.name() != null && !req.name().isBlank()) org.setName(req.name().trim());
        if (req.description() != null) org.setDescription(req.description());

        // Slug rename is rare and disruptive — refuse on collision rather
        // than silently rotate. The caller can retry with another value.
        if (req.slug() != null && !req.slug().isBlank()
            && !req.slug().trim().equalsIgnoreCase(org.getSlug())) {
            String newSlug = req.slug().trim();
            orgRepo.findBySlugIgnoreCase(newSlug).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new BusinessException("slug collision with org id=" + other.getId());
                }
            });
            org.setSlug(newSlug);
        }
        return orgRepo.save(org);
    }

    /**
     * Refuses to delete an org that still has projects. The caller
     * should re-home or delete the projects first.
     */
    @Audited("organization.delete")
    @Transactional
    public void deleteById(Long id) {
        Organization org = findById(id);

        List<Project> projects = projectRepo.findByOrganizationId(id);
        if (!projects.isEmpty()) {
            throw new BusinessException(
                "Cannot delete organization " + id + " — it still owns "
                + projects.size() + " project(s). Move or delete them first.");
        }

        // Wipe membership rows first (FK to org).
        List<OrganizationMember> members = memberRepo.findByOrgId(id);
        memberRepo.deleteAll(members);
        orgRepo.delete(org);
    }

    // ----- membership ops -----

    @Transactional(readOnly = true)
    public List<OrganizationMember> listMembers(Long orgId) {
        findById(orgId);   // existence + 404 if not found
        return memberRepo.findByOrgId(orgId);
    }

    /**
     * Upsert: if `userId` is already a member, overwrite the role;
     * otherwise insert a new row.
     *
     * Last-owner guard: if the upsert would DEMOTE the last remaining
     * OWNER (i.e., the only OWNER is being switched to MEMBER), refuse.
     */
    @Audited("organization.addMember")
    @Transactional
    public OrganizationMember addOrUpdateMember(Long orgId, AddOrgMemberRequest req) {
        findById(orgId);

        Optional<OrganizationMember> existing = memberRepo.findByOrgIdAndUserId(orgId, req.userId());
        if (existing.isPresent()) {
            OrganizationMember m = existing.get();
            if (m.getRole() == OrgRole.OWNER && req.role() != OrgRole.OWNER) {
                requireNotLastOwner(orgId, "demote");
            }
            m.setRole(req.role());
            return memberRepo.save(m);
        }
        return memberRepo.save(new OrganizationMember(orgId, req.userId(), req.role()));
    }

    /**
     * Remove a member. Self-leave is allowed for non-owners; an owner
     * may only leave if at least one other owner remains.
     */
    @Audited("organization.removeMember")
    @Transactional
    public void removeMember(Long orgId, Long userId) {
        OrganizationMember m = memberRepo.findByOrgIdAndUserId(orgId, userId)
            .orElseThrow(() -> new NotFoundException(
                "Not a member of org " + orgId + ": user " + userId));

        if (m.getRole() == OrgRole.OWNER) {
            requireNotLastOwner(orgId, "remove");
        }
        memberRepo.delete(m);
    }

    // ----- helpers -----

    private void requireNotLastOwner(Long orgId, String action) {
        long owners = memberRepo.countByOrgIdAndRole(orgId, OrgRole.OWNER);
        if (owners <= 1) {
            throw new BusinessException(
                "Cannot " + action + " the last OWNER of organization " + orgId
                + ". Promote another member first.");
        }
    }

    /**
     * Derive a slug from a free-text name. Lowercase, ASCII letters /
     * digits / dashes; collapses other characters to a single dash;
     * trims leading/trailing dashes.
     *
     * Deliberately conservative: this isn't a unicode-perfect slugger
     * (we'd reach for a library when we hit a non-Latin name) — it's
     * a "good enough for English + a fallback for everything else"
     * implementation that keeps the deps slim.
     */
    static String slugify(String name) {
        if (name == null) return "";
        StringBuilder out = new StringBuilder(name.length());
        boolean lastDash = true; // suppress leading dashes
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
                lastDash = false;
            } else if (!lastDash) {
                out.append('-');
                lastDash = true;
            }
        }
        // strip trailing dash
        if (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        return out.length() == 0 ? "org" : out.toString();
    }

}
