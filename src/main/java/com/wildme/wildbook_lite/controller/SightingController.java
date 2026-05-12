package com.wildme.wildbook_lite.controller;

import com.wildme.wildbook_lite.dto.CreateSightingRequest;
import com.wildme.wildbook_lite.entity.Sighting;
import com.wildme.wildbook_lite.service.SightingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sightings")
public class SightingController {

    private final SightingService service;

    public SightingController(SightingService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Sighting> list(
            @RequestParam(required = false) Long encounterId,
            @RequestParam(required = false) Long observerId,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.findAll(encounterId, observerId, pageable);
    }

    @GetMapping("/{id}")
    public Sighting get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public Sighting create(@Valid @RequestBody CreateSightingRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }
}
