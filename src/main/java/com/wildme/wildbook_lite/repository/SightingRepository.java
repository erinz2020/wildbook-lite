package com.wildme.wildbook_lite.repository;

import com.wildme.wildbook_lite.entity.Sighting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SightingRepository extends JpaRepository<Sighting, Long>, JpaSpecificationExecutor<Sighting> {
}
