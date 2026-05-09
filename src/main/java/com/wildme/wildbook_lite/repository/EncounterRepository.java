package com.wildme.wildbook_lite.repository;

import com.wildme.wildbook_lite.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

}