package com.wildme.wildbook_lite.annotation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnotationRepository extends JpaRepository<Annotation, Long> {

    List<Annotation> findByEncounterIdOrderByIdAsc(Long encounterId);

    /**
     * Bulk delete by encounter id — invoked from Encounter delete cascade.
     * Note that Features for each Annotation are wiped explicitly by
     * a separate bulk query (FeatureRepository.deleteByEncounterId)
     * BEFORE this runs, because Hibernate's auto-flush of @Modifying
     * won't load the children to fire orphanRemoval.
     */
    @Modifying
    @Query("delete from Annotation a where a.encounter.id = :encounterId")
    int deleteByEncounterId(@Param("encounterId") Long encounterId);
}
