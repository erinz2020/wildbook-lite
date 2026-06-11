package com.wildme.wildbook_lite.tag;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EncounterTagRepository extends JpaRepository<EncounterTag, Long> {

    List<EncounterTag> findByEncounterId(Long encounterId);

    Optional<EncounterTag> findByEncounterIdAndTagId(Long encounterId, Long tagId);

    /** Distinct encounter IDs that carry ALL of the given tag IDs. */
    @Query("""
        select et.encounterId from EncounterTag et
        where et.tagId in :tagIds
        group by et.encounterId
        having count(distinct et.tagId) = :tagCount
        """)
    List<Long> findEncounterIdsWithAllTags(@Param("tagIds") List<Long> tagIds,
                                           @Param("tagCount") long tagCount);

    @Modifying
    @Query("delete from EncounterTag et where et.encounterId = :encounterId")
    int deleteByEncounterId(@Param("encounterId") Long encounterId);
}
