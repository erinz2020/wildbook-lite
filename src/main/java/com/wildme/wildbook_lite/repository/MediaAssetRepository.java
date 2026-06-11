package com.wildme.wildbook_lite.repository;

import java.util.List;

import com.wildme.wildbook_lite.entity.MediaAsset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findByEncounterId(Long encounterId);

    @Modifying
    @Query("delete from MediaAsset m where m.encounter.id = :encounterId")
    int deleteByEncounterId(@Param("encounterId") Long encounterId);
}