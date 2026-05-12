package com.wildme.wildbook_lite.repository;

import java.util.List;

import com.wildme.wildbook_lite.entity.MediaAsset;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findByEncounterId(Long encounterId);
}