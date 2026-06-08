package com.wildme.wildbook_lite.notification;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * In-app notification (think GitHub's notifications inbox).
 *
 * Why a dedicated entity instead of just pushing to email/Slack:
 *  - Survives across sessions, queryable, paginated.
 *  - Decouples "what happened" from "where to deliver" — the inbox is
 *    one delivery channel; email / push can be added as additional
 *    channels driven by the same Notification rows.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "ix_notif_recipient_unread", columnList = "recipient_user_id, read_flag, created_at"),
        @Index(name = "ix_notif_recipient_created", columnList = "recipient_user_id, created_at")
    }
)
public class Notification extends BaseEntity {

    public enum Kind {
        ENCOUNTER_CREATED,
        COMMENT_ADDED,
        PROJECT_INVITED
    }

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Kind kind;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String body;

    /** Optional pointer to the entity that triggered this notification. */
    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "read_flag", nullable = false)
    private boolean read = false;

    public Notification() {}

    public Notification(Long recipientUserId, Kind kind, String title, String body,
                        String targetType, Long targetId) {
        this.recipientUserId = recipientUserId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public Long getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Long recipientUserId) { this.recipientUserId = recipientUserId; }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
