package com.wildme.wildbook_lite.service;

import java.util.List;
import java.util.UUID;

import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.MediaAsset;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.repository.MediaAssetRepository;
import com.wildme.wildbook_lite.storage.AssetStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaAssetService {

    private final MediaAssetRepository mediaRepo;
    private final EncounterRepository encRepo;
    private final AssetStore assetStore;
    private final ProjectGuard projectGuard;

    public MediaAssetService(MediaAssetRepository mediaRepo,
                             EncounterRepository encRepo,
                             AssetStore assetStore,
                             ProjectGuard projectGuard) {
        this.encRepo = encRepo;
        this.mediaRepo = mediaRepo;
        this.assetStore = assetStore;
        this.projectGuard = projectGuard;
    }

    @Transactional()
    public MediaAsset upload(
        Long encounterId,
        MultipartFile file) {

        //verify encounter exists
        Encounter enc = encRepo.findById(encounterId)
        .orElseThrow(() -> new NotFoundException("Encounter not found"));

        //verify caller can write to the encounter's project
        if (enc.getProjectId() != null && !projectGuard.canWrite(enc.getProjectId())) {
            throw new ForbiddenException("No write access to project: " + enc.getProjectId());
        }

        //verify file not null
        if(file.isEmpty()) {
            throw new BusinessException("File is empty");
        }

        //verify file size
        if(file.getSize() > 10 * 1024 * 1024) { // 10 MB limit
            throw new BusinessException("File is too large");
        }

        //generate safe file name
        String originalName = file.getOriginalFilename();
        if(originalName == null) {
            throw new BusinessException("File name is invalid");
        }
        String extension = originalName.substring(originalName.lastIndexOf("."));
        if(!(extension.equalsIgnoreCase(".jpg") || extension.equalsIgnoreCase(".png") || extension.equalsIgnoreCase(".jpeg"))) {
            throw new BusinessException("Invalid file type");
        }
        String safeName = UUID.randomUUID() + extension;

        //store file via AssetStore (disk, S3, etc)
        String filePath = assetStore.store(file, safeName);

        //create MediaAsset record, save to db
        MediaAsset asset = new MediaAsset();
        asset.setFileName(originalName);
        asset.setFilePath(filePath);
        asset.setFileSize(file.getSize());
        asset.setEncounter(enc);

        return mediaRepo.save(asset);
    }

    @Transactional()
    public  List<MediaAsset> findByEncounterId(Long encounterId) {
        encRepo.findById(encounterId)
        .orElseThrow(() -> new NotFoundException("Encounter not found"));

        return mediaRepo.findByEncounterId(encounterId);
    }
}