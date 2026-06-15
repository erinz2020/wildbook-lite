package com.wildme.wildbook_lite.annotation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureRepository extends JpaRepository<Feature, Long> {

    List<Feature> findByAnnotationId(Long annotationId);

    List<Feature> findByMediaAssetId(Long mediaAssetId);

    /**
     * Wipe every Feature row whose Annotation belongs to the given
     * Encounter. Used as the first step of the encounter-delete cascade
     * so we can then drop the Annotations safely.
     */
    @Modifying
    @Query("delete from Feature f where f.annotation.id in " +
           "(select a.id from Annotation a where a.encounter.id = :encounterId)")
    int deleteByEncounterId(@Param("encounterId") Long encounterId);

    /**
     * When a MediaAsset is deleted, every Feature that referenced it
     * must go too — otherwise we orphan the FK. Annotations themselves
     * are left intact (an annotation with zero Features is still
     * meaningful — it just means "we know there's an animal here, we
     * lost the image").
     */
    @Modifying
    @Query("delete from Feature f where f.mediaAsset.id = :mediaAssetId")
    int deleteByMediaAssetId(@Param("mediaAssetId") Long mediaAssetId);
}
