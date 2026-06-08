package com.wildme.wildbook_lite.audit;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async sink for AuditedEvent. Lives on its own thread pool so audit IO
 * never blocks user-facing requests, and uses REQUIRES_NEW so an audit
 * row is committed even when the originating business transaction
 * rolled back (FAIL audits are exactly the ones we want to keep!).
 */
@Component
public class AuditLogListener {

    private final AuditLogRepository repo;

    public AuditLogListener(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Async("applicationTaskExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAudited(AuditedEvent event) {
        AuditLog row = new AuditLog();
        row.setAction(event.action());
        row.setUserId(event.userId());
        row.setUsername(event.username());
        row.setArgs(event.args());
        row.setSuccess(event.success());
        row.setDurationMs(event.durationMs());
        row.setErrorClass(event.errorClass());
        row.setErrorMessage(event.errorMessage());
        row.setTraceId(event.traceId());
        repo.save(row);
    }
}
