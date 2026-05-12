package com.wildme.wildbook_lite.controller;

import com.wildme.wildbook_lite.dto.CreateObserverRequest;
import com.wildme.wildbook_lite.dto.UpdateObserverRequest;
import com.wildme.wildbook_lite.entity.Observer;
import com.wildme.wildbook_lite.service.ObserverService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/observers")
public class ObserverController {

    private final ObserverService service;

    public ObserverController(ObserverService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Observer> list(
            @RequestParam(required = false) String organization,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.findAll(organization, pageable);
    }

    @GetMapping("/{id}")
    public Observer get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public Observer create(@Valid @RequestBody CreateObserverRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public Observer update(@PathVariable Long id, @RequestBody UpdateObserverRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }
}
