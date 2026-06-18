package com.wildme.wildbook_lite.organization;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySlugIgnoreCase(String slug);
}
