package com.wildme.wildbook_lite.service;

import com.wildme.wildbook_lite.dto.CreateIndividualRequest;
import com.wildme.wildbook_lite.dto.UpdateIndividualRequest;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.repository.IndividualRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@Service
public class IndividualService {

    private final IndividualRepository repo;

    public IndividualService(IndividualRepository repo) {
        this.repo = repo;
    }

    public Page<Individual> findAll(
        String species,
        Pageable pageable) {
        Specification<Individual> spec = Specification.where((root, query, cb) -> null);

        if(species != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("species"), species));
        }

        return repo.findAll(spec, pageable);
    }

    public Individual findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Individual not found:" + id));
    }

    public void deleteById(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Individual not found:" + id);
        }
        repo.deleteById(id);
    }

    public Individual update(Long id, UpdateIndividualRequest request) {
        Individual individual = findById(id);
        if(request.nickname() != null) {
            individual.setNickname(request.nickname());
        }
        if(request.species() != null) {
            individual.setSpecies(request.species());
        }
        return repo.save(individual);
    }

    public Individual create(CreateIndividualRequest request) {
        Individual individual = new Individual();
        individual.setNickname(request.nickname());
        individual.setSpecies(request.species());
        return repo.save(individual);
    }
}