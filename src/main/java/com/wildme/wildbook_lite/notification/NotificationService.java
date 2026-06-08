package com.wildme.wildbook_lite.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.exception.NotFoundException;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Page<Notification> listMine(Boolean unreadOnly, Pageable pageable) {
        Long userId = SecurityUtils.currentUserId();
        if (Boolean.TRUE.equals(unreadOnly)) {
            return repo.findByRecipientUserIdAndReadOrderByCreatedAtDesc(userId, false, pageable);
        }
        return repo.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repo.countByRecipientUserIdAndRead(SecurityUtils.currentUserId(), false);
    }

    @Transactional
    public Notification markAsRead(Long id) {
        Long userId = SecurityUtils.currentUserId();
        Notification n = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("Notification not found: " + id));
        if (!n.getRecipientUserId().equals(userId)) {
            throw new ForbiddenException("Cannot mark someone else's notification as read");
        }
        n.setRead(true);
        return repo.save(n);
    }

    @Transactional
    public int markAllAsRead() {
        return repo.markAllAsRead(SecurityUtils.currentUserId());
    }
}
