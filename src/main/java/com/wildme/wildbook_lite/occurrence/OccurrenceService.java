package com.wildme.wildbook_lite.occurrence;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.occurrence.dto.CreateOccurrenceRequest;
import com.wildme.wildbook_lite.occurrence.dto.UpdateOccurrenceRequest;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.search.opensearch.EncounterChangedEvent;

/**
 * Aggregate-root service for Occurrence (the group sighting event).
 *
 * Owns:
 *   - CRUD on Occurrence
 *   - attach/detach an Encounter to/from an Occurrence
 *
 * Doesn't own:
 *   - Encounter creation (that's EncounterService) — we just point an
 *     existing Encounter at this Occurrence.
 *
 * Cross-cutting concerns honoured here:
 *   - @Audited on every write (AuditAspect picks them up)
 *   - ProjectGuard checks before any read/write (no project leaks)
 *   - EncounterChangedEvent fired whenever an Encounter's occurrence
 *     association changes, so OpenSearch / derived indexes can resync
 *     in the same way they do for direct mutations on Encounter.
 */
@Service
public class OccurrenceService {

    private final OccurrenceRepository occurrenceRepo;
    private final EncounterRepository encounterRepo;
    private final ProjectGuard projectGuard;
    private final ApplicationEventPublisher events;

    public OccurrenceService(OccurrenceRepository occurrenceRepo,
                             EncounterRepository encounterRepo,
                             ProjectGuard projectGuard,
                             ApplicationEventPublisher events) {
        this.occurrenceRepo = occurrenceRepo;
        this.encounterRepo = encounterRepo;
        this.projectGuard = projectGuard;
        this.events = events;
    }

    @Audited("occurrence.create")
    @Transactional
    public Occurrence create(CreateOccurrenceRequest req) {
        if (!projectGuard.canWrite(req.projectId())) {
            throw new ForbiddenException("No write access to project: " + req.projectId());
        }
        validateGroupSize(req.groupSizeMin(), req.groupSizeMax());

        Occurrence o = new Occurrence();
        o.setProjectId(req.projectId());
        o.setDateTime(req.dateTime());
        o.setLocation(req.location());
        o.setDecimalLatitude(req.decimalLatitude());
        o.setDecimalLongitude(req.decimalLongitude());
        o.setWeather(req.weather());
        o.setPlatform(req.platform());
        o.setTransect(req.transect());
        o.setGroupSizeMin(req.groupSizeMin());
        o.setGroupSizeMax(req.groupSizeMax());
        o.setNumAdults(req.numAdults());
        o.setNumJuveniles(req.numJuveniles());
        o.setNumCalves(req.numCalves());
        o.setComments(req.comments());
        o.setSubmitterUserId(SecurityUtils.currentUserId());
        return occurrenceRepo.save(o);
    }

    /**
     * Partial update — only fields with a non-null value in the request
     * are applied. projectId is NOT updatable (tenant boundary).
     */
    @Audited("occurrence.update")
    @Transactional
    public Occurrence update(Long id, UpdateOccurrenceRequest req) {
        Occurrence o = occurrenceRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Occurrence not found: " + id));
        requireWriteAccess(o);

        if (req.dateTime() != null)         o.setDateTime(req.dateTime());
        if (req.location() != null)         o.setLocation(req.location());
        if (req.decimalLatitude() != null)  o.setDecimalLatitude(req.decimalLatitude());
        if (req.decimalLongitude() != null) o.setDecimalLongitude(req.decimalLongitude());
        if (req.weather() != null)          o.setWeather(req.weather());
        if (req.platform() != null)         o.setPlatform(req.platform());
        if (req.transect() != null)         o.setTransect(req.transect());
        if (req.numAdults() != null)        o.setNumAdults(req.numAdults());
        if (req.numJuveniles() != null)     o.setNumJuveniles(req.numJuveniles());
        if (req.numCalves() != null)        o.setNumCalves(req.numCalves());
        if (req.comments() != null)         o.setComments(req.comments());

        // Group size has a coherence rule (min <= max). Re-validate if
        // either changed using the post-merge values.
        Integer newMin = req.groupSizeMin() != null ? req.groupSizeMin() : o.getGroupSizeMin();
        Integer newMax = req.groupSizeMax() != null ? req.groupSizeMax() : o.getGroupSizeMax();
        validateGroupSize(newMin, newMax);
        if (req.groupSizeMin() != null) o.setGroupSizeMin(newMin);
        if (req.groupSizeMax() != null) o.setGroupSizeMax(newMax);

        o.setUpdatedAt(LocalDateTime.now());
        return occurrenceRepo.save(o);
    }

    /**
     * Delete an Occurrence. Refuses if any Encounter still points at it —
     * the caller must explicitly detach the encounters first (or
     * delete them). This is the safe default: deleting a group event
     * out from under its members would orphan their context.
     *
     * Trade-off: a "force" flag would be nicer for cleanup scripts;
     * we'd add it as a separate /force endpoint to keep the default
     * call site obviously safe.
     */
    @Audited("occurrence.delete")
    @Transactional
    public void deleteById(Long id) {
        Occurrence o = occurrenceRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Occurrence not found: " + id));
        requireWriteAccess(o);

        if (!o.getEncounters().isEmpty()) {
            throw new BusinessException(
                "Cannot delete occurrence " + id + " — it still has "
                + o.getEncounters().size() + " encounter(s). Detach them first.");
        }
        occurrenceRepo.delete(o);
    }

    @Transactional(readOnly = true)
    public Occurrence findById(Long id) {
        Occurrence o = occurrenceRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Occurrence not found: " + id));
        requireReadAccess(o);
        // Force initialization of the lazy encounters collection inside
        // the tx, so the DTO mapper outside the tx can read it without
        // a LazyInitializationException.
        o.getEncounters().size();
        return o;
    }

    @Transactional(readOnly = true)
    public Page<Occurrence> findByProject(Long projectId,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          Pageable pageable) {
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }
        if (from != null && to != null) {
            return occurrenceRepo.findByProjectIdAndDateTimeBetweenOrderByDateTimeDesc(
                projectId, from, to, pageable);
        }
        return occurrenceRepo.findByProjectIdOrderByDateTimeDesc(projectId, pageable);
    }

    /**
     * Attach an existing Encounter to this Occurrence.
     *
     * Rules:
     *  - Occurrence and Encounter must live in the same project.
     *  - Caller must have write access to that project.
     *  - If the Encounter is already attached elsewhere, fail loudly —
     *    no silent re-parenting.
     *  - Fires EncounterChangedEvent so the search index resyncs.
     */
    @Audited("occurrence.attachEncounter")
    @Transactional
    public Encounter attachEncounter(Long occurrenceId, Long encounterId) {
        Occurrence o = occurrenceRepo.findById(occurrenceId)
            .orElseThrow(() -> new NotFoundException("Occurrence not found: " + occurrenceId));
        requireWriteAccess(o);

        Encounter e = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));

        if (e.getProjectId() == null || !e.getProjectId().equals(o.getProjectId())) {
            throw new BusinessException(
                "Encounter " + encounterId + " (project=" + e.getProjectId()
                + ") cannot be attached to occurrence " + occurrenceId
                + " (project=" + o.getProjectId() + ")");
        }
        if (e.getOccurrence() != null) {
            if (e.getOccurrence().getId().equals(occurrenceId)) {
                return e;  // idempotent
            }
            throw new BusinessException(
                "Encounter " + encounterId + " is already attached to occurrence "
                + e.getOccurrence().getId() + "; detach it first");
        }

        e.setOccurrence(o);
        Encounter saved = encounterRepo.save(e);
        events.publishEvent(new EncounterChangedEvent(saved.getId(), EncounterChangedEvent.Kind.UPSERT));
        return saved;
    }

    /**
     * Detach an Encounter from this Occurrence. Idempotent: detaching
     * an Encounter that isn't attached (or is attached to a different
     * Occurrence) is a no-op rather than an error — same shape as
     * "unfollow" semantics.
     */
    @Audited("occurrence.detachEncounter")
    @Transactional
    public Encounter detachEncounter(Long occurrenceId, Long encounterId) {
        Occurrence o = occurrenceRepo.findById(occurrenceId)
            .orElseThrow(() -> new NotFoundException("Occurrence not found: " + occurrenceId));
        requireWriteAccess(o);

        Encounter e = encounterRepo.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));

        if (e.getOccurrence() == null || !e.getOccurrence().getId().equals(occurrenceId)) {
            return e;  // not attached to *this* occurrence — no-op
        }
        e.setOccurrence(null);
        Encounter saved = encounterRepo.save(e);
        events.publishEvent(new EncounterChangedEvent(saved.getId(), EncounterChangedEvent.Kind.UPSERT));
        return saved;
    }

    // ----- helpers -----

    private void validateGroupSize(Integer min, Integer max) {
        if (min != null && min < 0) {
            throw new BusinessException("groupSizeMin must be >= 0");
        }
        if (max != null && max < 0) {
            throw new BusinessException("groupSizeMax must be >= 0");
        }
        if (min != null && max != null && min > max) {
            throw new BusinessException("groupSizeMin (" + min + ") > groupSizeMax (" + max + ")");
        }
    }

    private void requireReadAccess(Occurrence o) {
        if (o.getProjectId() != null && !projectGuard.canRead(o.getProjectId())) {
            throw new ForbiddenException("No read access to occurrence: " + o.getId());
        }
    }

    private void requireWriteAccess(Occurrence o) {
        if (o.getProjectId() != null && !projectGuard.canWrite(o.getProjectId())) {
            throw new ForbiddenException("No write access to occurrence: " + o.getId());
        }
    }
}
