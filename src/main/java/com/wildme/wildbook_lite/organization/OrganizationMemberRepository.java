package com.wildme.wildbook_lite.organization;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    Optional<OrganizationMember> findByOrgIdAndUserId(Long orgId, Long userId);

    List<OrganizationMember> findByOrgId(Long orgId);

    List<OrganizationMember> findByUserId(Long userId);

    /** Used by last-owner protection: refuse to demote / remove if this returns 1. */
    long countByOrgIdAndRole(Long orgId, OrgRole role);
}
