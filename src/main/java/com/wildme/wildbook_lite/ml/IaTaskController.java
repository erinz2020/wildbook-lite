package com.wildme.wildbook_lite.ml;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.ml.dto.AcceptMatchRequest;
import com.wildme.wildbook_lite.ml.dto.CreateIaTaskRequest;
import com.wildme.wildbook_lite.ml.dto.CreateIndividualFromMatchRequest;
import com.wildme.wildbook_lite.ml.dto.IaTaskResponse;
import com.wildme.wildbook_lite.ml.dto.MatchResultPageResponse;
import com.wildme.wildbook_lite.ml.dto.SkipMatchRequest;

import jakarta.validation.Valid;

/**
 * REST surface for the async identification pipeline.
 *
 *   POST   /api/ia-tasks                       enqueue (returns 202)
 *   GET    /api/ia-tasks/{id}                  poll for status + result
 *   GET    /api/ia-tasks?annotationId=&page=   list tasks for an annotation
 *   POST   /api/ia-tasks/{id}/cancel           user cancel (PENDING only)
 *
 * The POST returns 202 Accepted instead of 200/201 — convention for
 * "we've accepted your job, check back later". The response body is
 * already populated with id + status=PENDING.
 */
@RestController
@RequestMapping("/api/ia-tasks")
public class IaTaskController {

    /** Default cap on candidates returned by the page endpoint. Matches Wildbook's MAX_NUM_RESULTS-ish. */
    private static final int DEFAULT_TOP_N = 5;
    /** Hard ceiling so a hostile `?topN=100000` can't blow the payload. */
    private static final int MAX_TOP_N = 50;

    private final IaTaskService service;
    private final IaResolutionService resolutionService;

    public IaTaskController(IaTaskService service, IaResolutionService resolutionService) {
        this.service = service;
        this.resolutionService = resolutionService;
    }

    @PostMapping
    public ResponseEntity<IaTaskResponse> enqueue(@Valid @RequestBody CreateIaTaskRequest req) {
        IaTask task = service.enqueue(req.annotationId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(IaTaskResponse.from(task));
    }

    @GetMapping("/{id}")
    public IaTaskResponse get(@PathVariable Long id) {
        return IaTaskResponse.from(service.findById(id));
    }

    @GetMapping
    public Page<IaTaskResponse> list(@RequestParam Long annotationId,
                                     @PageableDefault(size = 20) Pageable pageable) {
        return service.listByAnnotation(annotationId, pageable).map(IaTaskResponse::from);
    }

    @PostMapping("/{id}/cancel")
    public IaTaskResponse cancel(@PathVariable Long id) {
        return IaTaskResponse.from(service.cancel(id));
    }

    // ===== Match-result page + resolution actions =====

    /**
     * Full match-result page payload for the review UI. One request
     * gets task status, query annotation summary, top-N candidates,
     * and current resolution state.
     *
     * GET /api/ia-tasks/{taskId}/match-result?topN=5
     */
    @GetMapping("/{taskId}/match-result")
    public MatchResultPageResponse matchResult(
            @PathVariable Long taskId,
            @RequestParam(required = false) Integer topN) {
        int n = topN == null ? DEFAULT_TOP_N : Math.min(Math.max(topN, 1), MAX_TOP_N);
        return MatchResultPageResponse.from(service.findById(taskId), n);
    }

    /**
     * Reviewer accepted one of the candidates: assign the encounter to
     * that Individual + record the decision on the MatchResult.
     */
    @PostMapping("/{taskId}/accept")
    public MatchResultPageResponse accept(@PathVariable Long taskId,
                                          @Valid @RequestBody AcceptMatchRequest req) {
        return MatchResultPageResponse.from(
            resolutionService.accept(taskId, req), DEFAULT_TOP_N);
    }

    /**
     * Reviewer rejected all candidates and is registering a brand-new
     * Individual.
     */
    @PostMapping("/{taskId}/create-individual")
    public MatchResultPageResponse createIndividual(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateIndividualFromMatchRequest req) {
        return MatchResultPageResponse.from(
            resolutionService.createIndividualFromQuery(taskId, req), DEFAULT_TOP_N);
    }

    /**
     * Reviewer explicitly declined to decide for now. The encounter
     * stays untouched but the audit fact is recorded.
     */
    @PostMapping("/{taskId}/skip")
    public MatchResultPageResponse skip(@PathVariable Long taskId,
                                        @RequestBody(required = false) SkipMatchRequest req) {
        return MatchResultPageResponse.from(
            resolutionService.skip(taskId, req == null ? new SkipMatchRequest(null) : req),
            DEFAULT_TOP_N);
    }
}
