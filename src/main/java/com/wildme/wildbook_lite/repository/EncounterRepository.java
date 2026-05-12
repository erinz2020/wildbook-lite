package com.wildme.wildbook_lite.repository;

import com.wildme.wildbook_lite.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface EncounterRepository extends JpaRepository<Encounter, Long>, JpaSpecificationExecutor<Encounter> {
    Page<Encounter> findBySpecies(String species, Pageable pageable);
    Page<Encounter> findByLocation(String location, Pageable pageable);
    Page<Encounter> findBySpeciesAndLocation(String species, String location, Pageable pageable);
}