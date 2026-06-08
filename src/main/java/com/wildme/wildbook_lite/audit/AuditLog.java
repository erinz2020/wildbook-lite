package com.wildme.wildbook_lite.audit;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One row per @Audited method invocation.
 *
 * Why a separate table instead of just SLF4J logs:
 *  - Queryable: "show me everything user 42 did in the last 7 days".
 *  - Survives log rotation.
 *  - Cheap join against users.
 *
 * Indexed on (userId, createdAt desc) for the typical "user activity"
 * query pattern.
 */
@Entity
@Table(
    name = "audit_log",
    indexes = {
        @Index(name = "ix_audit_user_time", columnList = "user_id, created_at"),
        @Index(name = "ix_audit_action_time", columnList = "action, created_at")
    }
)
public class AuditLog extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String action;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64)
    private String username;

    @Column(name = "args", length = 1000)
    private String args;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "error_class", length = 128)
    private String errorClass;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    public AuditLog() {}

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getArgs() { return args; }
    public void setArgs(String args) { this.args = args; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getErrorClass() { return errorClass; }
    public void setErrorClass(String errorClass) { this.errorClass = errorClass; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
