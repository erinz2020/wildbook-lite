package com.wildme.wildbook_lite.controller;

import com.wildme.wildbook_lite.dto.CreateIndividualRequest;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.service.IndividualService;
import com.wildme.wildbook_lite.dto.UpdateIndividualRequest;
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
@RequestMapping("/api/individuals")
public class IndividualController {

    private final IndividualService service;

    public IndividualController(IndividualService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Individual> list(
        @RequestParam(required = false) String species,
        @PageableDefault(size = 50) 
        Pageable pageable) {
        return service.findAll(species, pageable);
    }

    @GetMapping("/{id}")
    public Individual get(@PathVariable Long id) {
        return service.findById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }

    @PatchMapping("/{id}")
    public Individual update(@PathVariable Long id, @RequestBody UpdateIndividualRequest request) {
        return service.update(id, request);
    }

    @PostMapping
    public Individual create(@Valid @RequestBody CreateIndividualRequest request) {
        return service.create(request);
    }
}