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

import com.wildme.wildbook_lite.dto.AssignEncounterRequest;
import com.wildme.wildbook_lite.dto.BulkResult;
import com.wildme.wildbook_lite.dto.BulkTransitionRequest;
import com.wildme.wildbook_lite.dto.CreateEncounterRequest;
import com.wildme.wildbook_lite.dto.TransitionEncounterRequest;
import com.wildme.wildbook_lite.dto.UpdateEncounterRequest;
import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.encounter.EncounterStatusHistory;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.service.EncounterBulkService;
import com.wildme.wildbook_lite.service.EncounterService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private final EncounterService service;
    private final EncounterBulkService bulkService;

    public EncounterController(EncounterService service, EncounterBulkService bulkService) {
        this.service = service;
        this.bulkService = bulkService;
    }

    /**
     * GET /api/encounters?projectId=1
     *                    &species=...           (optional)
     *                    &location=...          (optional)
     *                    &status=PUBLISHED      (optional)
     *                    &assignedToUserId=42   (optional)
     *                    &tagIds=4&tagIds=7     (optional, AND semantics)
     *                    &page=0&size=20
     */
    @GetMapping
    public Page<Encounter> list(
            @RequestParam Long projectId,
            @RequestParam(required = false) String species,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) EncounterStatus status,
            @RequestParam(required = false) Long assignedToUserId,
            @RequestParam(required = false) List<Long> tagIds,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.findAll(projectId, species, location, status,
                               assignedToUserId, tagIds, pageable);
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

    @PostMapping("/{id}/transition")
    public Encounter transition(@PathVariable Long id,
                                @Valid @RequestBody TransitionEncounterRequest request) {
        return service.transition(id, request.toStatus());
    }

    /** Workflow timeline: every status change in chronological order. */
    @GetMapping("/{id}/history")
    public List<EncounterStatusHistory> history(@PathVariable Long id) {
        return service.listHistory(id);
    }

    /** Assign (or re-assign) the encounter to a project member. */
    @PostMapping("/{id}/assign")
    public Encounter assign(@PathVariable Long id,
                            @Valid @RequestBody AssignEncounterRequest request) {
        return service.assignTo(id, request.userId());
    }

    /**
     * Best-effort batch transition. Per-row sub-transactions, so a
     * partial failure does not roll back the successes; the response
     * lists which ids failed and why.
     */
    @PostMapping("/bulk-transition")
    public BulkResult bulkTransition(@Valid @RequestBody BulkTransitionRequest request) {
        return bulkService.bulkTransition(request.ids(), request.toStatus());
    }

    /** Same partial-failure semantics, for bulk deletes. */
    @DeleteMapping("/bulk")
    public BulkResult bulkDelete(@RequestParam List<Long> ids) {
        return bulkService.bulkDelete(ids);
    }
}
