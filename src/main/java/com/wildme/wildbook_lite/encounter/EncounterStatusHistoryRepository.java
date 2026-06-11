package com.wildme.wildbook_lite.encounter;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EncounterStatusHistoryRepository extends JpaRepository<EncounterStatusHistory, Long> {

    List<EncounterStatusHistory> findByEncounterIdOrderByCreatedAtAsc(Long encounterId);

    @Modifying
    @Query("delete from EncounterStatusHistory h where h.encounterId = :encounterId")
    int deleteByEncounterId(@Param("encounterId") Long encounterId);
}
