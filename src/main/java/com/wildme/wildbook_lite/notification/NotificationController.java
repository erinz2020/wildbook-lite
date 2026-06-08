package com.wildme.wildbook_lite.notification;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.auth.AppPrincipal;
import com.wildme.wildbook_lite.auth.CurrentUser;
import com.wildme.wildbook_lite.common.PageResponse;
import com.wildme.wildbook_lite.notification.dto.NotificationResponse;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> listMine(
            @RequestParam(required = false) Boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.listMine(unreadOnly, pageable), NotificationResponse::from);
    }

    /**
     * Demo of the @CurrentUser custom argument resolver — the controller
     * receives the authenticated principal as a typed parameter, instead
     * of pulling it from SecurityContextHolder manually.
     */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(@CurrentUser AppPrincipal me) {
        return Map.of(
            "user", me.getUsername(),
            "count", service.unreadCount()
        );
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return NotificationResponse.from(service.markAsRead(id));
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllAsRead());
    }
}
