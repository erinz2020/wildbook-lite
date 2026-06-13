package com.wildme.wildbook_lite.occurrence;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.occurrence.dto.CreateOccurrenceRequest;
import com.wildme.wildbook_lite.occurrence.dto.OccurrenceResponse;
import com.wildme.wildbook_lite.occurrence.dto.UpdateOccurrenceRequest;

import jakarta.validation.Valid;

/**
 * REST surface for the survey-event aggregate root.
 *
 *   POST   /api/occurrences                       create
 *   GET    /api/occurrences?projectId=&from=&to=  list (paginated, project-scoped)
 *   GET    /api/occurrences/{id}                  detail (with encounter summary)
 *   PATCH  /api/occurrences/{id}                  partial update
 *   DELETE /api/occurrences/{id}                  hard delete (refuses if has encounters)
 *
 *   POST   /api/occurrences/{id}/encounters/{encId}    attach existing encounter
 *   DELETE /api/occurrences/{id}/encounters/{encId}    detach encounter (no-op if not attached)
 *
 * Note: the *list* endpoint returns raw Occurrence pages (no encounter
 * summaries — would N+1). The *detail* endpoint wraps in
 * OccurrenceResponse, which includes encounterCount + species list.
 */
@RestController
@RequestMapping("/api/occurrences")
public class OccurrenceController {

    private final OccurrenceService service;

    public OccurrenceController(OccurrenceService service) {
        this.service = service;
    }

    @PostMapping
    public Occurrence create(@Valid @RequestBody CreateOccurrenceRequest req) {
        return service.create(req);
    }

    @GetMapping
    public Page<Occurrence> list(
            @RequestParam Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.findByProject(projectId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public OccurrenceResponse get(@PathVariable Long id) {
        return OccurrenceResponse.from(service.findById(id));
    }

    @PatchMapping("/{id}")
    public Occurrence update(@PathVariable Long id,
                             @RequestBody UpdateOccurrenceRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteById(id);
    }

    @PostMapping("/{id}/encounters/{encId}")
    public Encounter attach(@PathVariable Long id, @PathVariable Long encId) {
        return service.attachEncounter(id, encId);
    }

    @DeleteMapping("/{id}/encounters/{encId}")
    public Encounter detach(@PathVariable Long id, @PathVariable Long encId) {
        return service.detachEncounter(id, encId);
    }
}
