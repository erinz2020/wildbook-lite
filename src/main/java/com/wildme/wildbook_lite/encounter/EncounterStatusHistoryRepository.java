package com.wildme.wildbook_lite.encounter;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterStatusHistoryRepository extends JpaRepository<EncounterStatusHistory, Long> {

    List<EncounterStatusHistory> findByEncounterIdOrderByCreatedAtAsc(Long encounterId);
}
