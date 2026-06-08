package com.wildme.wildbook_lite.tag;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Many-to-many between Encounter and Tag, modeled as a first-class entity
 * for the same reason ProjectMember is: we want audit columns, and we
 * may eventually carry data on the relationship (e.g., who tagged it).
 */
@Entity
@Table(
    name = "encounter_tags",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_encounter_tag", columnNames = {"encounter_id", "tag_id"}),
    indexes = {
        @Index(name = "ix_etag_encounter", columnList = "encounter_id"),
        @Index(name = "ix_etag_tag", columnList = "tag_id")
    }
)
public class EncounterTag extends BaseEntity {

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "tagged_by_user_id", nullable = false)
    private Long taggedByUserId;

    public EncounterTag() {}

    public EncounterTag(Long encounterId, Long tagId, Long taggedByUserId) {
        this.encounterId = encounterId;
        this.tagId = tagId;
        this.taggedByUserId = taggedByUserId;
    }

    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }

    public Long getTaggedByUserId() { return taggedByUserId; }
    public void setTaggedByUserId(Long taggedByUserId) { this.taggedByUserId = taggedByUserId; }
}
