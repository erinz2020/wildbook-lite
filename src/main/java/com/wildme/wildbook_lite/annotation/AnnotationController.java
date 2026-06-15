package com.wildme.wildbook_lite.annotation;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.annotation.dto.AnnotationResponse;
import com.wildme.wildbook_lite.annotation.dto.CreateAnnotationRequest;
import com.wildme.wildbook_lite.annotation.dto.UpdateAnnotationRequest;

import jakarta.validation.Valid;

/**
 * REST surface for image annotations.
 *
 *   POST   /api/encounters/{encId}/annotations          create
 *   GET    /api/encounters/{encId}/annotations          list under encounter
 *
 *   GET    /api/annotations/{id}                        detail (with Features)
 *   PATCH  /api/annotations/{id}                        partial update
 *   DELETE /api/annotations/{id}                        delete (cascades Features)
 *
 * Split path layout: creation/listing is encounter-scoped because that
 * mirrors how the UI talks about it ("annotations of this encounter").
 * Per-id ops live at the flat /api/annotations base because callers
 * shouldn't need to re-derive the parent encounter just to update one
 * field.
 */
@RestController
public class AnnotationController {

    private final AnnotationService service;

    public AnnotationController(AnnotationService service) {
        this.service = service;
    }

    @PostMapping("/api/encounters/{encId}/annotations")
    public AnnotationResponse create(@PathVariable Long encId,
                                     @Valid @RequestBody CreateAnnotationRequest req) {
        return AnnotationResponse.from(service.create(encId, req));
    }

    @GetMapping("/api/encounters/{encId}/annotations")
    public List<AnnotationResponse> listByEncounter(@PathVariable Long encId) {
        return service.listByEncounter(encId).stream()
            .map(AnnotationResponse::from)
            .toList();
    }

    @GetMapping("/api/annotations/{id}")
    public AnnotationResponse get(@PathVariable Long id) {
        return AnnotationResponse.from(service.findById(id));
    }

    @PatchMapping("/api/annotations/{id}")
    public AnnotationResponse update(@PathVariable Long id,
                                     @RequestBody UpdateAnnotationRequest req) {
        return AnnotationResponse.from(service.update(id, req));
    }

    @DeleteMapping("/api/annotations/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
