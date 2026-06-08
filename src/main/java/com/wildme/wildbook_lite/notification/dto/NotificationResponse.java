package com.wildme.wildbook_lite.notification.dto;

import java.time.Instant;

import com.wildme.wildbook_lite.notification.Notification;

public record NotificationResponse(
    Long id,
    Notification.Kind kind,
    String title,
    String body,
    String targetType,
    Long targetId,
    boolean read,
    Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
            n.getId(),
            n.getKind(),
            n.getTitle(),
            n.getBody(),
            n.getTargetType(),
            n.getTargetId(),
            n.isRead(),
            n.getCreatedAt()
        );
    }
}
