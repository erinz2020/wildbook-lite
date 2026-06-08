package com.wildme.wildbook_lite.service;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.dto.CreateEncounterRequest;
import com.wildme.wildbook_lite.dto.UpdateEncounterRequest;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.notification.EncounterCreatedEvent;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.repository.IndividualRepository;

@Service
public class EncounterService {

    private final EncounterRepository encRepo;
    private final IndividualRepository indRepo;
    private final ProjectGuard projectGuard;
    private final ApplicationEventPublisher events;

    public EncounterService(EncounterRepository encRepo,
                            IndividualRepository indRepo,
                            ProjectGuard projectGuard,
                            ApplicationEventPublisher events) {
        this.encRepo = encRepo;
        this.indRepo = indRepo;
        this.projectGuard = projectGuard;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<Encounter> findAll(Long projectId, String species, String location, Pageable pageable) {
        if (projectId == null) {
            throw new BusinessException("projectId is required for listing encounters");
        }
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }

        Specification<Encounter> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (species != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("species"), species));
        }
        if (location != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("location"), location));
        }
        return encRepo.findAll(spec, pageable);
    }

    /**
     * Cached by id. Note: cache stores the *entity*, permission check still
     * runs for every caller — safe to share across users.
     *
     * Cache miss → DB hit → result stored. Cache hit → no DB call.
     */
    @Cacheable(value = "encounter", key = "#id")
    @Transactional(readOnly = true)
    public Encounter findById(Long id) {
        Encounter e = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
        requireReadAccess(e);
        return e;
    }

    @Audited("encounter.delete")
    @CacheEvict(value = "encounter", key = "#id")
    @Transactional
    public void deleteById(Long id) {
        Encounter e = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
        requireWriteAccess(e);
        encRepo.delete(e);
    }

    @Audited("encounter.update")
    @CacheEvict(value = "encounter", key = "#id")
    @Transactional
    public Encounter update(Long id, UpdateEncounterRequest request) {
        Encounter encounter = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
        requireWriteAccess(encounter);

        if (request.location() != null) encounter.setLocation(request.location());
        if (request.species()  != null) encounter.setSpecies(request.species());
        return encRepo.save(encounter);
    }

    @Audited("encounter.create")
    @Transactional
    public Encounter create(CreateEncounterRequest request) {
        if (!projectGuard.canWrite(request.projectId())) {
            throw new ForbiddenException("No write access to project: " + request.projectId());
        }
        Encounter encounter = new Encounter();
        encounter.setProjectId(request.projectId());
        encounter.setLocation(request.location());
        encounter.setSpecies(request.species());
        Encounter saved = encRepo.save(encounter);

        events.publishEvent(new EncounterCreatedEvent(
            saved.getId(),
            saved.getProjectId(),
            SecurityUtils.currentUserId(),
            Instant.now()
        ));
        return saved;
    }

    @Transactional
    public Encounter assignIndividual(Long id, UpdateEncounterRequest request) {
        Encounter enc = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter id not found: " + id));
        requireWriteAccess(enc);

        Long indId = request.individualId();
        Individual ind = indRepo.findById(indId)
            .orElseThrow(() -> new NotFoundException("Individual id not found: " + indId));
        if (enc.getSpecies() != null && !enc.getSpecies().equals(ind.getSpecies())) {
            throw new BusinessException("Species mismatch between encounter and individual");
        }
        enc.setIndividual(ind);
        return encRepo.save(enc);
    }

    /**
     * Streaming export. Consumer is invoked for each row WITHOUT holding the
     * full result set in memory. The DB cursor + entity lifecycle stay inside
     * this @Transactional boundary; if the consumer throws, the tx rolls back.
     *
     * Why callback-style and not "return Stream<Encounter>": the JPA stream
     * is bound to a Session that closes when this method returns, so the
     * controller wouldn't be able to consume it. Forcing the work into the
     * service keeps the contract clean.
     */
    @Transactional(readOnly = true)
    public void streamByProject(Long projectId, Consumer<Encounter> consumer) {
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }
        try (Stream<Encounter> stream = encRepo.streamByProjectId(projectId)) {
            stream.forEach(consumer);
        }
    }

    // ----- permission helpers -----

    private void requireReadAccess(Encounter e) {
        if (e.getProjectId() != null && !projectGuard.canRead(e.getProjectId())) {
            throw new ForbiddenException("No read access to encounter: " + e.getId());
        }
    }

    private void requireWriteAccess(Encounter e) {
        if (e.getProjectId() != null && !projectGuard.canWrite(e.getProjectId())) {
            throw new ForbiddenException("No write access to encounter: " + e.getId());
        }
    }
}
