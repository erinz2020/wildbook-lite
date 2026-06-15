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

import com.wildme.wildbook_lite.ml.dto.CreateIaTaskRequest;
import com.wildme.wildbook_lite.ml.dto.IaTaskResponse;

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

    private final IaTaskService service;

    public IaTaskController(IaTaskService service) {
        this.service = service;
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
}
