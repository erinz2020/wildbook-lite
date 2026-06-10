package com.wildme.wildbook_lite.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.audit.dto.AuditLogResponse;
import com.wildme.wildbook_lite.auth.AppPrincipal;
import com.wildme.wildbook_lite.auth.CurrentUser;
import com.wildme.wildbook_lite.common.PageResponse;

/**
 * Read-only endpoints over the audit_log table.
 *
 *   /api/audit-logs           ADMIN  — full audit visibility, optionally
 *                                       filtered by ?action= or ?userId=
 *   /api/audit-logs/me        any user — caller's own history
 *
 * Two paths instead of one filter: a non-admin user must NOT be able
 * to pass ?userId=42 to read someone else's trail. Separate endpoints
 * make the authorization rule a property of the URL, not of a runtime
 * if-statement that's easy to forget.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository repo;

    public AuditLogController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/me")
    public PageResponse<AuditLogResponse> mine(
            @CurrentUser AppPrincipal me,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<AuditLog> page = repo.findByUserIdOrderByCreatedAtDesc(me.getUserId(), pageable);
        return PageResponse.from(page, AuditLogResponse::from);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<AuditLog> page;
        if (action != null) {
            page = repo.findByActionOrderByCreatedAtDesc(action, pageable);
        } else if (userId != null) {
            page = repo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else {
            page = repo.findAll(pageable);
        }
        return PageResponse.from(page, AuditLogResponse::from);
    }
}
