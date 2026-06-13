package com.wildme.wildbook_lite.occurrence;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OccurrenceRepository extends JpaRepository<Occurrence, Long>,
                                                JpaSpecificationExecutor<Occurrence> {

    Page<Occurrence> findByProjectIdOrderByDateTimeDesc(Long projectId, Pageable pageable);

    Page<Occurrence> findByProjectIdAndDateTimeBetweenOrderByDateTimeDesc(
        Long projectId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Detach every encounter pointing at this occurrence — used right
     * before deleting the occurrence. @Modifying makes JPA execute it
     * as a bulk UPDATE (no entity load, no per-row dirty check).
     *
     * The clearAutomatically=true would also be reasonable, but our
     * delete flow already manages the session carefully, so we skip it
     * to keep behavior explicit.
     */
    @Modifying
    @Query("update Encounter e set e.occurrence = null where e.occurrence.id = :occurrenceId")
    int detachAllEncounters(@Param("occurrenceId") Long occurrenceId);
}
