package com.wildme.wildbook_lite.tag;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.tag.dto.CreateTagRequest;

@Service
public class TagService {

    private final TagRepository tagRepo;
    private final EncounterTagRepository encTagRepo;
    private final EncounterRepository encRepo;
    private final ProjectGuard projectGuard;

    public TagService(TagRepository tagRepo,
                      EncounterTagRepository encTagRepo,
                      EncounterRepository encRepo,
                      ProjectGuard projectGuard) {
        this.tagRepo = tagRepo;
        this.encTagRepo = encTagRepo;
        this.encRepo = encRepo;
        this.projectGuard = projectGuard;
    }

    @Transactional
    public Tag create(Long projectId, CreateTagRequest req) {
        if (!projectGuard.canWrite(projectId)) {
            throw new ForbiddenException("No write access to project: " + projectId);
        }
        if (tagRepo.existsByProjectIdAndName(projectId, req.name())) {
            throw new BusinessException("Tag already exists in this project: " + req.name());
        }
        return tagRepo.save(new Tag(projectId, req.name(), req.color()));
    }

    @Transactional(readOnly = true)
    public List<Tag> listByProject(Long projectId) {
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }
        return tagRepo.findByProjectId(projectId);
    }

    @Transactional
    public void delete(Long projectId, Long tagId) {
        if (!projectGuard.canWrite(projectId)) {
            throw new ForbiddenException("No write access to project: " + projectId);
        }
        Tag tag = tagRepo.findById(tagId)
            .orElseThrow(() -> new NotFoundException("Tag not found: " + tagId));
        if (!tag.getProjectId().equals(projectId)) {
            throw new NotFoundException("Tag not in project: " + tagId);
        }
        tagRepo.delete(tag);
    }

    @Transactional
    public EncounterTag attach(Long encounterId, Long tagId) {
        Encounter enc = encRepo.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        if (enc.getProjectId() != null && !projectGuard.canWrite(enc.getProjectId())) {
            throw new ForbiddenException("No write access to encounter's project");
        }
        Tag tag = tagRepo.findById(tagId)
            .orElseThrow(() -> new NotFoundException("Tag not found: " + tagId));
        if (!tag.getProjectId().equals(enc.getProjectId())) {
            throw new BusinessException("Tag belongs to a different project");
        }
        return encTagRepo.findByEncounterIdAndTagId(encounterId, tagId)
            .orElseGet(() -> encTagRepo.save(
                new EncounterTag(encounterId, tagId, SecurityUtils.currentUserId())
            ));
    }

    @Transactional
    public void detach(Long encounterId, Long tagId) {
        Encounter enc = encRepo.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        if (enc.getProjectId() != null && !projectGuard.canWrite(enc.getProjectId())) {
            throw new ForbiddenException("No write access to encounter's project");
        }
        EncounterTag link = encTagRepo.findByEncounterIdAndTagId(encounterId, tagId)
            .orElseThrow(() -> new NotFoundException("Tag is not attached to this encounter"));
        encTagRepo.delete(link);
    }

    @Transactional(readOnly = true)
    public List<EncounterTag> listForEncounter(Long encounterId) {
        Encounter enc = encRepo.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        if (enc.getProjectId() != null && !projectGuard.canRead(enc.getProjectId())) {
            throw new ForbiddenException("No read access to encounter's project");
        }
        return encTagRepo.findByEncounterId(encounterId);
    }
}
