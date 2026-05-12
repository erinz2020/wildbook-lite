package com.wildme.wildbook_lite.service;

import com.wildme.wildbook_lite.dto.CreateEncounterRequest;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.dto.UpdateEncounterRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@Service
public class EncounterService {

    private final EncounterRepository repo;

    public EncounterService(EncounterRepository repo) {
        this.repo = repo;
    }

    public Page<Encounter> findAll(
        String species,
        String location,
        Pageable pageable) {
        Specification<Encounter> spec = Specification.where((root, query, cb) -> null);

        if(species != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("species"), species));
        }

        if(location != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("location"), location));
        }

        return repo.findAll(spec, pageable);
    }

    public Encounter findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Encounter not found:" + id));
    }

    public void deleteById(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Encounter not found:" + id);
        }
        repo.deleteById(id);
    }

    public Encounter update(Long id, UpdateEncounterRequest request) {
        Encounter encounter = findById(id);
        if(request.location() != null) {
            encounter.setLocation(request.location());
        }
        if(request.species() != null) {
            encounter.setSpecies(request.species());
        }
        return repo.save(encounter);
    }

    public Encounter create(CreateEncounterRequest request) {
        Encounter encounter = new Encounter();
        encounter.setLocation(request.location());
        encounter.setSpecies(request.species());
        return repo.save(encounter);
    }
}