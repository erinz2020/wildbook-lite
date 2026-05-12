package com.wildme.wildbook_lite.controller;

import com.wildme.wildbook_lite.dto.CreateEncounterRequest;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.service.EncounterService;
import com.wildme.wildbook_lite.dto.UpdateEncounterRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private final EncounterService service;

    public EncounterController(EncounterService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Encounter> list(
        @RequestParam(required = false) String species,
        @RequestParam(required = false) String location,
        @PageableDefault(size = 50) 
        Pageable pageable) {
        return service.findAll(species, location, pageable);
    }

    @GetMapping("/{id}")
    public Encounter get(@PathVariable Long id) {
        return service.findById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }

    @PatchMapping("/{id}")
    public Encounter update(@PathVariable Long id, @RequestBody UpdateEncounterRequest request) {
        return service.update(id, request);
    }

    @PostMapping
    public Encounter create(@Valid @RequestBody CreateEncounterRequest request) {
        return service.create(request);
    }
}