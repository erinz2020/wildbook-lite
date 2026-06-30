package com.wildme.wildbook_lite.bulkimport;

import java.util.Random;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates Resilience4j against a flaky external service — a stand-in for
 * the image-analysis (IA) enqueue API a bulk import would call after creating
 * records. The real method would make an HTTP call; this stub fails
 * intermittently so the retry + circuit breaker can be observed.
 *
 * Like @Async/@Transactional, these annotations work via a Spring proxy, so
 * this must be a separate bean and called cross-bean to take effect.
 */
@Component
@Slf4j
public class ExternalAnalysisClient {

    private final Random random = new Random();

    /**
     * @Retry        — re-invoke on failure (attempts/backoff configured in application.yml).
     * @CircuitBreaker — after too many failures, "open" and short-circuit to the
     *                   fallback instead of hammering a service that's down.
     */
    @Retry(name = "iaService")
    @CircuitBreaker(name = "iaService", fallbackMethod = "enqueueFallback")
    public String enqueue(Long taskId) {
        log.info("[ia] calling external analysis service for task {}", taskId);
        if (random.nextInt(10) < 6) {            // ~60% failure, to exercise retry
            throw new RuntimeException("external IA service unavailable");
        }
        return "IA-JOB-" + taskId;
    }

    /**
     * Fallback: invoked when retries are exhausted or the breaker is open.
     * Signature must match the guarded method plus a trailing Throwable.
     */
    private String enqueueFallback(Long taskId, Throwable t) {
        log.warn("[ia] fallback for task {} — deferring: {}", taskId, t.toString());
        return "IA-DEFERRED-" + taskId;
    }
}
