package com.wildme.wildbook_lite.audit;

/**
 * Fired by AuditAspect, consumed by AuditLogListener. Plain record so the
 * publisher and the listener don't share a transactional context.
 */
public record AuditedEvent(
    String action,
    Long userId,
    String username,
    String args,
    boolean success,
    long durationMs,
    String errorClass,
    String errorMessage,
    String traceId
) {}
