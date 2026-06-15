package com.wildme.wildbook_lite.taxonomy;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.taxonomy.dto.CreateTaxonomyRequest;
import com.wildme.wildbook_lite.taxonomy.dto.UpdateTaxonomyRequest;

import jakarta.validation.Valid;

/**
 * Species catalogue REST surface.
 *
 *   Reads (any authenticated user):
 *     GET /api/taxonomy?q=...    search
 *     GET /api/taxonomy/{id}     detail
 *
 *   Writes (ADMIN only — system reference data):
 *     POST   /api/taxonomy
 *     PATCH  /api/taxonomy/{id}
 *     DELETE /api/taxonomy/{id}
 *
 * The ADMIN gate is method-level @PreAuthorize so SecurityConfig's
 * global filter chain stays unchanged.
 */
@RestController
@RequestMapping("/api/taxonomy")
public class TaxonomyController {

    private final TaxonomyService service;

    public TaxonomyController(TaxonomyService service) {
        this.service = service;
    }

    @GetMapping
    public List<Taxonomy> list(@RequestParam(required = false) String q) {
        return service.search(q);
    }

    @GetMapping("/{id}")
    public Taxonomy get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Taxonomy create(@Valid @RequestBody CreateTaxonomyRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Taxonomy update(@PathVariable Long id,
                           @Valid @RequestBody UpdateTaxonomyRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }
}
