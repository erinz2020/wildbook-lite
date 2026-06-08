package com.wildme.wildbook_lite.repository;

import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.wildme.wildbook_lite.entity.Encounter;

import jakarta.persistence.QueryHint;


public interface EncounterRepository extends JpaRepository<Encounter, Long>, JpaSpecificationExecutor<Encounter> {

    Page<Encounter> findBySpecies(String species, Pageable pageable);
    Page<Encounter> findByLocation(String location, Pageable pageable);
    Page<Encounter> findBySpeciesAndLocation(String species, String location, Pageable pageable);

    /**
     * Streaming read for CSV export. The hint tells Hibernate to use a
     * server-side cursor (fetch a chunk at a time) instead of pulling all
     * rows into memory.
     *
     * Caller MUST consume inside a @Transactional method and close the
     * stream (try-with-resources) — otherwise the cursor leaks.
     */
    @QueryHints(@QueryHint(name = org.hibernate.jpa.AvailableHints.HINT_FETCH_SIZE, value = "200"))
    @Query("select e from Encounter e where e.projectId = :projectId order by e.id")
    Stream<Encounter> streamByProjectId(@Param("projectId") Long projectId);
}
