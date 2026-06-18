package com.wildme.wildbook_lite.project;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** Drives "list this org's projects" + the org-delete safety check (count > 0 → refuse). */
    List<Project> findByOrganizationId(Long organizationId);
}
