package com.wildme.wildbook_lite.repository;

import java.util.List;

import com.wildme.wildbook_lite.entity.Sighting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SightingRepository extends JpaRepository<Sighting, Long>, JpaSpecificationExecutor<Sighting> {

    List<Sighting> findByEncounterId(Long encounterId);

    /**
     * Bulk delete by parent FK. Bypasses the persistence context — fast,
     * doesn't fire @PreRemove listeners. Safe here because Sighting has
     * no lifecycle callbacks we care about.
     */
    @Modifying
    @Query("delete from Sighting s where s.encounter.id = :encounterId")
    int deleteByEncounterId(@Param("encounterId") Long encounterId);
}
