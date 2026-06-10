package com.wildme.wildbook_lite.encounter;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One row per status transition. Two reasons this exists alongside
 * the generic `audit_log` table:
 *
 *  - audit_log is a *technical* audit (every @Audited method call,
 *    used for forensics).
 *  - encounter_status_history is *business* state: "show me the
 *    workflow timeline of this encounter, with who and when".
 *    Frontends render it as a vertical timeline. Querying audit_log
 *    for the same view would be possible but slow and brittle.
 *
 * fromStatus is nullable so the very first row (created in DRAFT) can
 * be modeled as null -> DRAFT.
 */
@Entity
@Table(
    name = "encounter_status_history",
    indexes = {
        @Index(name = "ix_history_encounter_time", columnList = "encounter_id, created_at")
    }
)
public class EncounterStatusHistory extends BaseEntity {

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private EncounterStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private EncounterStatus toStatus;

    @Column(name = "changed_by_user_id", nullable = false)
    private Long changedByUserId;

    @Column(length = 500)
    private String comment;

    public EncounterStatusHistory() {}

    public EncounterStatusHistory(Long encounterId,
                                  EncounterStatus fromStatus,
                                  EncounterStatus toStatus,
                                  Long changedByUserId,
                                  String comment) {
        this.encounterId = encounterId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.comment = comment;
    }

    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }

    public EncounterStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(EncounterStatus fromStatus) { this.fromStatus = fromStatus; }

    public EncounterStatus getToStatus() { return toStatus; }
    public void setToStatus(EncounterStatus toStatus) { this.toStatus = toStatus; }

    public Long getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Long changedByUserId) { this.changedByUserId = changedByUserId; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
