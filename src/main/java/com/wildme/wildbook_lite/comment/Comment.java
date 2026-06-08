package com.wildme.wildbook_lite.comment;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "comments",
    indexes = {
        @Index(name = "ix_comments_encounter", columnList = "encounter_id"),
        @Index(name = "ix_comments_author", columnList = "author_user_id")
    }
)
public class Comment extends BaseEntity {

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(nullable = false, length = 2000)
    private String body;

    public Comment() {}

    public Comment(Long encounterId, Long authorUserId, String body) {
        this.encounterId = encounterId;
        this.authorUserId = authorUserId;
        this.body = body;
    }

    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }

    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
