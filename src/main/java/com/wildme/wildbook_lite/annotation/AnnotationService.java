package com.wildme.wildbook_lite.annotation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.annotation.dto.CreateAnnotationRequest;
import com.wildme.wildbook_lite.annotation.dto.UpdateAnnotationRequest;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.MediaAsset;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.repository.MediaAssetRepository;

/**
 * Service for image annotations.
 *
 * Hard rule: every annotation lives under exactly one Encounter.
 *           Every Feature points at exactly one MediaAsset, and that
 *           MediaAsset must already belong to the same Encounter (no
 *           cross-encounter image sharing — would silently break the
 *           project-isolation guarantee).
 *
 * Why we don't cascade MediaAsset delete to Annotation:
 *   - A reviewer may have hand-annotated a fluke years ago and we lose
 *     the photo. The annotation row still records "we knew about this
 *     animal here at this time" — historical value > storage cost.
 *   - We DO cascade-delete the Features (they're meaningless without
 *     the image they bridge). See FeatureRepository.deleteByMediaAssetId.
 */
@Service
public class AnnotationService {

    private final AnnotationRepository annotationRepo;
    private final FeatureRepository featureRepo;
    private final EncounterRepository encounterRepo;
    private final MediaAssetRepository mediaRepo;
    private final ProjectGuard projectGuard;

    public AnnotationService(AnnotationRepository annotationRepo,
                             FeatureRepository featureRepo,
                             EncounterRepository encounterRepo,
                             MediaAssetRepository mediaRepo,
                             ProjectGuard projectGuard) {
        this.annotationRepo = annotationRepo;
        this.featureRepo = featureRepo;
        this.encounterRepo = encounterRepo;
        this.mediaRepo = mediaRepo;
        this.projectGuard = projectGuard;
    }

    @Audited("annotation.create")
    @Transactional
    public Annotation create(Long encounterId, CreateAnnotationRequest req) {
        Encounter enc = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        if (enc.getProjectId() != null && !projectGuard.canWrite(enc.getProjectId())) {
            throw new ForbiddenException("No write access to encounter: " + encounterId);
        }

        MediaAsset media = mediaRepo.findById(req.mediaAssetId())
            .orElseThrow(() -> new NotFoundException("MediaAsset not found: " + req.mediaAssetId()));
        // The image must already be attached to THIS encounter — otherwise
        // we'd be cross-linking a photo from another encounter.
        if (media.getEncounter() == null || !media.getEncounter().getId().equals(encounterId)) {
            throw new BusinessException(
                "MediaAsset " + req.mediaAssetId() + " is not attached to encounter " + encounterId);
        }

        // bbox coherence: either all 4 corner params are present (BBOX
        // type) or none are (TRIVIAL type). Partial input is rejected
        // — that's almost certainly a client bug we'd rather surface.
        FeatureType type = inferFeatureType(req);

        Annotation a = new Annotation();
        a.setEncounter(enc);
        if (type == FeatureType.BBOX) {
            a.setX(req.x());
            a.setY(req.y());
            a.setWidth(req.width());
            a.setHeight(req.height());
            a.setTheta(req.theta() == null ? 0.0 : req.theta());
        }
        a.setSpecies(req.species());
        a.setViewpoint(req.viewpoint() == null ? Viewpoint.UNKNOWN : req.viewpoint());
        a.setQuality(req.quality());
        a.setExemplar(Boolean.TRUE.equals(req.exemplar()));

        // Cascade.ALL on Annotation.features means saving the annotation
        // also persists the Feature — we don't need a featureRepo.save here.
        Feature f = new Feature(a, media, type);
        a.getFeatures().add(f);

        return annotationRepo.save(a);
    }

    @Audited("annotation.update")
    @Transactional
    public Annotation update(Long id, UpdateAnnotationRequest req) {
        Annotation a = annotationRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Annotation not found: " + id));
        requireWriteAccess(a);

        if (req.x() != null)         a.setX(req.x());
        if (req.y() != null)         a.setY(req.y());
        if (req.width() != null)     a.setWidth(req.width());
        if (req.height() != null)    a.setHeight(req.height());
        if (req.theta() != null)     a.setTheta(req.theta());
        if (req.species() != null)   a.setSpecies(req.species());
        if (req.viewpoint() != null) a.setViewpoint(req.viewpoint());
        if (req.quality() != null)   a.setQuality(req.quality());
        if (req.exemplar() != null)  a.setExemplar(req.exemplar());

        a.setUpdatedAt(LocalDateTime.now());
        return annotationRepo.save(a);
    }

    @Audited("annotation.delete")
    @Transactional
    public void delete(Long id) {
        Annotation a = annotationRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Annotation not found: " + id));
        requireWriteAccess(a);
        // CascadeType.ALL + orphanRemoval on Annotation.features wipes
        // the Feature rows; the MediaAsset stays.
        annotationRepo.delete(a);
    }

    @Transactional(readOnly = true)
    public Annotation findById(Long id) {
        Annotation a = annotationRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Annotation not found: " + id));
        requireReadAccess(a);
        // Force the lazy collection to load inside the tx so DTO mapping
        // outside the tx doesn't blow up.
        a.getFeatures().size();
        return a;
    }

    @Transactional(readOnly = true)
    public List<Annotation> listByEncounter(Long encounterId) {
        Encounter enc = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        if (enc.getProjectId() != null && !projectGuard.canRead(enc.getProjectId())) {
            throw new ForbiddenException("No read access to encounter: " + encounterId);
        }
        List<Annotation> result = annotationRepo.findByEncounterIdOrderByIdAsc(encounterId);
        // Pre-touch features for each one — single tx so the controller
        // can DTO-map outside without LazyInitializationException.
        for (Annotation a : result) a.getFeatures().size();
        return result;
    }

    // ----- helpers -----

    private FeatureType inferFeatureType(CreateAnnotationRequest req) {
        boolean anyBbox = req.x() != null || req.y() != null
            || req.width() != null || req.height() != null;
        boolean allBbox = req.x() != null && req.y() != null
            && req.width() != null && req.height() != null;

        if (anyBbox && !allBbox) {
            throw new BusinessException(
                "Partial bbox: x/y/width/height must all be supplied together");
        }
        if (allBbox) {
            if (req.width() <= 0 || req.height() <= 0) {
                throw new BusinessException("bbox width/height must be > 0");
            }
            return FeatureType.BBOX;
        }
        return FeatureType.TRIVIAL;
    }

    private void requireReadAccess(Annotation a) {
        Long projectId = a.getEncounter() == null ? null : a.getEncounter().getProjectId();
        if (projectId != null && !projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to annotation: " + a.getId());
        }
    }

    private void requireWriteAccess(Annotation a) {
        Long projectId = a.getEncounter() == null ? null : a.getEncounter().getProjectId();
        if (projectId != null && !projectGuard.canWrite(projectId)) {
            throw new ForbiddenException("No write access to annotation: " + a.getId());
        }
    }
}
