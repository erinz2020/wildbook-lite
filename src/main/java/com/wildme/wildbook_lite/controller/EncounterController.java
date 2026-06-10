package com.wildme.wildbook_lite.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.dto.CreateEncounterRequest;
import com.wildme.wildbook_lite.dto.TransitionEncounterRequest;
import com.wildme.wildbook_lite.dto.UpdateEncounterRequest;
import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.service.EncounterService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private final EncounterService service;

    public EncounterController(EncounterService service) {
        this.service = service;
    }

    /**
     * GET /api/encounters?projectId=1
     *                    &species=Humpback%20whale       (optional)
     *                    &location=Maui                  (optional)
     *                    &status=PUBLISHED               (optional)
     *                    &tagIds=4&tagIds=7              (optional, AND semantics)
     *                    &page=0&size=20                 (Spring Pageable)
     *
     * Spring MVC binds the *repeated* `tagIds` query parameter to a
     * List<Long> automatically. No custom converter needed.
     */
    @GetMapping
    public Page<Encounter> list(
            @RequestParam Long projectId,
            @RequestParam(required = false) String species,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) EncounterStatus status,
            @RequestParam(required = false) List<Long> tagIds,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.findAll(projectId, species, location, status, tagIds, pageable);
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

    @PatchMapping("/{id}/individual")
    public Encounter assignIndividual(@PathVariable Long id, @RequestBody UpdateEncounterRequest request) {
        return service.assignIndividual(id, request);
    }

    /**
     * State-machine transition. Body specifies the target state; the
     * service validates the (current → target) edge and the caller's
     * project role.
     *
     *   POST /api/encounters/42/transition
     *   { "toStatus": "PUBLISHED" }
     *
     * Why a dedicated endpoint and not just PATCH /api/encounters/42
     * with `{ "status": "PUBLISHED" }`:
     *   - A transition is an *action* with side effects (events,
     *     audits, notifications) — not just a property write. Making
     *     it its own resource ("the transitions of encounter 42")
     *     keeps the HTTP semantics honest.
     *   - 405-style attempts to set status via PATCH would also lose
     *     the role check; routing all state moves through one method
     *     means there's exactly one place to audit / enforce.
     */
    @PostMapping("/{id}/transition")
    public Encounter transition(@PathVariable Long id,
                                @Valid @RequestBody TransitionEncounterRequest request) {
        return service.transition(id, request.toStatus());
    }
}
