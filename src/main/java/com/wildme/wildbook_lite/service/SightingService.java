package com.wildme.wildbook_lite.service;

import com.wildme.wildbook_lite.dto.CreateSightingRequest;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.Observer;
import com.wildme.wildbook_lite.entity.Sighting;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.repository.ObserverRepository;
import com.wildme.wildbook_lite.repository.SightingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class SightingService {

    private final SightingRepository sightingRepo;
    private final EncounterRepository encounterRepo;
    private final ObserverRepository observerRepo;

    public SightingService(SightingRepository sightingRepo,
                           EncounterRepository encounterRepo,
                           ObserverRepository observerRepo) {
        this.sightingRepo = sightingRepo;
        this.encounterRepo = encounterRepo;
        this.observerRepo = observerRepo;
    }

    public Page<Sighting> findAll(Long encounterId, Long observerId, Pageable pageable) {
        Specification<Sighting> spec = Specification.where((root, query, cb) -> null);

        if (encounterId != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("encounter").get("id"), encounterId));
        }
        if (observerId != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("observer").get("id"), observerId));
        }

        return sightingRepo.findAll(spec, pageable);
    }

    public Sighting findById(Long id) {
        return sightingRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Sighting not found: " + id));
    }

    public Sighting create(CreateSightingRequest request) {
        Encounter encounter = encounterRepo.findById(request.encounterId())
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + request.encounterId()));

        Observer observer = null;
        if (request.observerId() != null) {
            observer = observerRepo.findById(request.observerId())
                    .orElseThrow(() -> new NotFoundException("Observer not found: " + request.observerId()));
        }

        Sighting sighting = new Sighting();
        sighting.setEncounter(encounter);
        sighting.setObserver(observer);
        sighting.setNotes(request.notes());
        return sightingRepo.save(sighting);
    }

    public void deleteById(Long id) {
        if (!sightingRepo.existsById(id)) {
            throw new NotFoundException("Sighting not found: " + id);
        }
        sightingRepo.deleteById(id);
    }
}
