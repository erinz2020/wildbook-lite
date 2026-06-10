package com.wildme.wildbook_lite.audit.dto;

import java.time.Instant;

import com.wildme.wildbook_lite.audit.AuditLog;

public record AuditLogResponse(
    Long id,
    String action,
    Long userId,
    String username,
    String args,
    boolean success,
    long durationMs,
    String errorClass,
    String errorMessage,
    String traceId,
    Instant createdAt
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
            a.getId(), a.getAction(), a.getUserId(), a.getUsername(),
            a.getArgs(), a.isSuccess(), a.getDurationMs(),
            a.getErrorClass(), a.getErrorMessage(),
            a.getTraceId(), a.getCreatedAt()
        );
    }
}
