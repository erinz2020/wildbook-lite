package com.wildme.wildbook_lite.service;

import com.wildme.wildbook_lite.dto.CreateObserverRequest;
import com.wildme.wildbook_lite.dto.UpdateObserverRequest;
import com.wildme.wildbook_lite.entity.Observer;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.repository.ObserverRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ObserverService {

    private final ObserverRepository repo;

    public ObserverService(ObserverRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Page<Observer> findAll(String organization, Pageable pageable) {
        Specification<Observer> spec = Specification.where((root, query, cb) -> null);

        if (organization != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("organization"), organization));
        }

        return repo.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Observer findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Observer not found: " + id));
    }

    @Transactional
    public void deleteById(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Observer not found: " + id);
        }
        repo.deleteById(id);
    }

    @Transactional
    public Observer update(Long id, UpdateObserverRequest request) {
        Observer observer = findById(id);
        if (request.name() != null) {
            observer.setName(request.name());
        }
        if (request.email() != null) {
            observer.setEmail(request.email());
        }
        if (request.organization() != null) {
            observer.setOrganization(request.organization());
        }
        return repo.save(observer);
    }

    @Transactional
    public Observer create(CreateObserverRequest request) {
        Observer observer = new Observer();
        observer.setName(request.name());
        observer.setEmail(request.email());
        observer.setOrganization(request.organization());
        return repo.save(observer);
    }
}
