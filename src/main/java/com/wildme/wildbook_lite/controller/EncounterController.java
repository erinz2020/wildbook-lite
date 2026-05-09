package com.wildme.wildbook_lite.controller;

import java.util.List;

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

@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private final EncounterService service;

    public EncounterController(EncounterService service) {
        this.service = service;
    }

    @GetMapping
    public List<Encounter> list() {
        return service.findAll();
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