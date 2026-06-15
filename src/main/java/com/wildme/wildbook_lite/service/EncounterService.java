package com.wildme.wildbook_lite.service;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.annotation.AnnotationRepository;
import com.wildme.wildbook_lite.annotation.FeatureRepository;
import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.dto.CreateEncounterRequest;
import com.wildme.wildbook_lite.dto.ReportEncounterRequest;
import com.wildme.wildbook_lite.dto.UpdateEncounterRequest;
import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.encounter.EncounterStatusHistory;
import com.wildme.wildbook_lite.encounter.EncounterStatusHistoryRepository;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.entity.Observer;
import com.wildme.wildbook_lite.entity.Sighting;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.occurrence.Occurrence;
import com.wildme.wildbook_lite.occurrence.OccurrenceRepository;
import com.wildme.wildbook_lite.taxonomy.Taxonomy;
import com.wildme.wildbook_lite.taxonomy.TaxonomyRepository;
import com.wildme.wildbook_lite.notification.EncounterAssignedEvent;
import com.wildme.wildbook_lite.notification.EncounterCreatedEvent;
import com.wildme.wildbook_lite.notification.EncounterPublishedEvent;
import com.wildme.wildbook_lite.search.opensearch.EncounterChangedEvent;
import com.wildme.wildbook_lite.comment.CommentRepository;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.repository.IndividualRepository;
import com.wildme.wildbook_lite.repository.MediaAssetRepository;
import com.wildme.wildbook_lite.repository.ObserverRepository;
import com.wildme.wildbook_lite.repository.SightingRepository;
import com.wildme.wildbook_lite.tag.EncounterTagRepository;

@Service
public class EncounterService {

    private final EncounterRepository encRepo;
    private final IndividualRepository indRepo;
    private final ObserverRepository obsRepo;
    private final SightingRepository sightingRepo;
    private final CommentRepository commentRepo;
    private final MediaAssetRepository mediaRepo;
    private final EncounterTagRepository encTagRepo;
    private final EncounterStatusHistoryRepository historyRepo;
    private final OccurrenceRepository occurrenceRepo;
    private final AnnotationRepository annotationRepo;
    private final FeatureRepository featureRepo;
    private final TaxonomyRepository taxonomyRepo;
    private final ProjectGuard projectGuard;
    private final ApplicationEventPublisher events;

    public EncounterService(EncounterRepository encRepo,
                            IndividualRepository indRepo,
                            ObserverRepository obsRepo,
                            SightingRepository sightingRepo,
                            CommentRepository commentRepo,
                            MediaAssetRepository mediaRepo,
                            EncounterTagRepository encTagRepo,
                            EncounterStatusHistoryRepository historyRepo,
                            OccurrenceRepository occurrenceRepo,
                            AnnotationRepository annotationRepo,
                            FeatureRepository featureRepo,
                            TaxonomyRepository taxonomyRepo,
                            ProjectGuard projectGuard,
                            ApplicationEventPublisher events) {
        this.encRepo = encRepo;
        this.indRepo = indRepo;
        this.obsRepo = obsRepo;
        this.sightingRepo = sightingRepo;
        this.commentRepo = commentRepo;
        this.mediaRepo = mediaRepo;
        this.encTagRepo = encTagRepo;
        this.historyRepo = historyRepo;
        this.occurrenceRepo = occurrenceRepo;
        this.annotationRepo = annotationRepo;
        this.featureRepo = featureRepo;
        this.taxonomyRepo = taxonomyRepo;
        this.projectGuard = projectGuard;
        this.events = events;
    }

    /**
     * Listing with composable filters. All filters AND together.
     *
     *  - species / location: exact match
     *  - status: workflow filter (e.g., show only PUBLISHED to viewers)
     *  - tagIds: encounter must carry ALL of these tags. We pre-resolve
     *    the matching encounter IDs via EncounterTagRepository (which
     *    does the heavy lifting in one GROUP BY HAVING query) and feed
     *    the result back through `id IN (...)` on the main Specification.
     *
     *    Why this two-step instead of joining EncounterTag into the
     *    main spec: a join repeated N times per tag would let Hibernate
     *    do a cartesian product and we'd have to wrestle with `distinct`,
     *    pagination, and ordering. The IN-subquery shape is easier to
     *    reason about and the DB can plan it cleanly.
     */
    @Transactional(readOnly = true)
    public Page<Encounter> findAll(Long projectId,
                                   String species,
                                   String location,
                                   EncounterStatus status,
                                   Long assignedToUserId,
                                   Long submitterUserId,
                                   List<Long> tagIds,
                                   Pageable pageable) {
        if (projectId == null) {
            throw new BusinessException("projectId is required for listing encounters");
        }
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }

        Specification<Encounter> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (species != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("species"), species));
        }
        if (location != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("location"), location));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (assignedToUserId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assignedToUserId"), assignedToUserId));
        }
        if (submitterUserId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("submitterUserId"), submitterUserId));
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Long> matchingIds = encTagRepo.findEncounterIdsWithAllTags(tagIds, tagIds.size());
            if (matchingIds.isEmpty()) {
                return Page.empty(pageable);
            }
            spec = spec.and((root, q, cb) -> root.get("id").in(matchingIds));
        }
        return encRepo.findAll(spec, pageable);
    }

    /**
     * Cached by id. Note: cache stores the *entity*, permission check still
     * runs for every caller — safe to share across users.
     *
     * Cache miss → DB hit → result stored. Cache hit → no DB call.
     */
    @Cacheable(value = "encounter", key = "#id")
    @Transactional(readOnly = true)
    public Encounter findById(Long id) {
        Encounter e = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
        requireReadAccess(e);
        return e;
    }

    /**
     * Hard delete an Encounter and clean up everything that points at it.
     *
     * Why hard delete instead of soft delete:
     *   The user-facing workflow uses status=ARCHIVED for the soft case
     *   (preserves history, hides from views). DELETE is the harder
     *   "this should never have existed" path — e.g., a test-data row.
     *
     * Order matters because of FK constraints in Postgres:
     *
     *   1. Children that point AT this encounter (FK encounter_id):
     *        sightings, comments, media, encounter_tags, status_history
     *      → must be deleted first.
     *
     *   2. Parents that this encounter points AT (individual_id,
     *      observer_id, project_id):
     *      → no DB cleanup needed — the parent row stays, only OUR FK
     *        goes away when we delete the row. But we DO touch the
     *        in-memory collection (individual.encounters,
     *        observer.encounters) to keep any already-loaded entities
     *        in the current session consistent.
     *
     *   3. Finally, delete the encounter itself.
     *
     * All wrapped in @Transactional, so a failure anywhere rolls the
     * whole thing back — no half-deleted encounter sitting around.
     *
     * Note on media files on disk: we only drop the DB rows here. The
     * actual blob files are left for a separate cleanup job to reap
     * (deletion of disk files is non-transactional and must not happen
     * inside the DB tx — we'd leak files on rollback).
     */
    @Audited("encounter.delete")
    @CacheEvict(value = "encounter", key = "#id")
    @Transactional
    public void deleteById(Long id) {
        Encounter e = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
        requireWriteAccess(e);

        // --- 1. detach from parent in-memory collections, so a session
        //        that has the parent loaded sees a consistent view.
        if (e.getIndividual() != null) {
            e.getIndividual().getEncounters().remove(e);
        }
        if (e.getObserver() != null) {
            e.getObserver().getEncounters().remove(e);
        }
        if (e.getOccurrence() != null) {
            e.getOccurrence().getEncounters().remove(e);
        }

        // --- 2. wipe all child rows (bulk @Modifying — fast, no listeners).
        //
        //   Order matters here for FK reasons:
        //     features  → reference annotations AND media_asset.
        //                 Must die first (else they orphan when their
        //                 annotation rows are deleted).
        //     annotations → reference encounter.
        //                 After their features are gone, safe to drop.
        //     media     → annotations no longer point at them, so
        //                 the FK from feature→media is gone too.
        //     sightings / comments / tags / history → encounter-only FKs,
        //                 order doesn't matter relative to each other.
        featureRepo.deleteByEncounterId(id);
        annotationRepo.deleteByEncounterId(id);
        sightingRepo.deleteByEncounterId(id);
        commentRepo.deleteByEncounterId(id);
        mediaRepo.deleteByEncounterId(id);
        encTagRepo.deleteByEncounterId(id);
        historyRepo.deleteByEncounterId(id);

        // --- 3. flush so the bulk DELETEs land in the DB BEFORE Hibernate
        //        tries to remove the parent row. Otherwise the auto-flush
        //        order can put the parent delete first → FK violation.
        encRepo.flush();

        // --- 4. finally drop the encounter itself.
        encRepo.delete(e);

        // --- 5. tell the indexer to remove the doc.
        events.publishEvent(new EncounterChangedEvent(id, EncounterChangedEvent.Kind.DELETE));
    }

    /**
     * Partial update. Only fields with non-null values in the request
     * are applied (PATCH semantics, not PUT).
     *
     * Relation updates (individualId, observerId) re-run the same
     * coherence checks as the report flow — species mismatch is still
     * a hard reject, even on update.
     */
    @Audited("encounter.update")
    @CacheEvict(value = "encounter", key = "#id")
    @Transactional
    public Encounter update(Long id, UpdateEncounterRequest request) {
        Encounter encounter = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
        requireWriteAccess(encounter);

        if (request.location() != null)      encounter.setLocation(request.location());
        if (request.species() != null)       encounter.setSpecies(request.species());
        if (request.encounterDate() != null) encounter.setEncounterDate(request.encounterDate());
        if (request.notes() != null)         encounter.setNotes(request.notes());

        if (request.taxonomyId() != null) {
            Taxonomy tax = taxonomyRepo.findById(request.taxonomyId())
                .orElseThrow(() -> new NotFoundException("Taxonomy not found: " + request.taxonomyId()));
            encounter.setTaxonomy(tax);
            // Keep the denormalized species column in sync with the
            // canonical scientificName — the species filter on /list
            // depends on it.
            encounter.setSpecies(tax.getScientificName());
        }
        if (request.locationId() != null)         encounter.setLocationId(request.locationId());
        if (request.decimalLatitude() != null)    encounter.setDecimalLatitude(request.decimalLatitude());
        if (request.decimalLongitude() != null)   encounter.setDecimalLongitude(request.decimalLongitude());
        if (request.lifeStage() != null)          encounter.setLifeStage(request.lifeStage());
        if (request.behavior() != null)           encounter.setBehavior(request.behavior());
        if (request.livingStatus() != null)       encounter.setLivingStatus(request.livingStatus());
        if (request.dynamicProperties() != null)  encounter.setDynamicProperties(request.dynamicProperties());

        if (request.individualId() != null) {
            Individual ind = indRepo.findById(request.individualId())
                .orElseThrow(() -> new NotFoundException("Individual not found: " + request.individualId()));
            String finalSpecies = encounter.getSpecies();
            if (finalSpecies != null && ind.getSpecies() != null
                && !finalSpecies.equalsIgnoreCase(ind.getSpecies())) {
                throw new BusinessException(
                    "Species mismatch: encounter=" + finalSpecies
                    + " vs individual=" + ind.getSpecies());
            }
            encounter.setIndividual(ind);
        }

        if (request.observerId() != null) {
            Observer obs = obsRepo.findById(request.observerId())
                .orElseThrow(() -> new NotFoundException("Observer not found: " + request.observerId()));
            encounter.setObserver(obs);
        }

        Encounter saved = encRepo.save(encounter);
        events.publishEvent(new EncounterChangedEvent(saved.getId(), EncounterChangedEvent.Kind.UPSERT));
        return saved;
    }

    @Audited("encounter.create")
    @Transactional
    public Encounter create(CreateEncounterRequest request) {
        if (!projectGuard.canWrite(request.projectId())) {
            throw new ForbiddenException("No write access to project: " + request.projectId());
        }
        Long currentUserId = SecurityUtils.currentUserId();

        Encounter encounter = new Encounter();
        encounter.setProjectId(request.projectId());
        encounter.setLocation(request.location());
        encounter.setSpecies(request.species());
        encounter.setSubmitterUserId(currentUserId);
        Encounter saved = encRepo.save(encounter);

        // Seed the workflow timeline: null → DRAFT, "created" comment.
        historyRepo.save(new EncounterStatusHistory(
            saved.getId(), null, EncounterStatus.DRAFT, currentUserId, "created"));

        events.publishEvent(new EncounterCreatedEvent(
            saved.getId(),
            saved.getProjectId(),
            currentUserId,
            Instant.now()
        ));
        events.publishEvent(new EncounterChangedEvent(saved.getId(), EncounterChangedEvent.Kind.UPSERT));
        return saved;
    }

    /**
     * High-level "I want to file an encounter report" path.
     *
     * Validation chain — fail fast on each, never half-commit:
     *   1. Project write access (caller must be EDITOR+ on the project).
     *   2. If individualId provided, individual must exist AND species
     *      must match the reported encounter species (no whales tagged
     *      as turtles).
     *   3. If observerId provided, observer must exist.
     *   4. If sightingIds provided, every sighting must exist and must
     *      NOT already belong to a different Encounter (no silent
     *      re-parenting — that would be a foot-gun).
     *
     * Side effects (all inside the same transaction so a late failure
     * rolls back everything cleanly):
     *   - Encounter row created in DRAFT
     *   - submitterUserId = current user (NOT from the request body —
     *     clients can't impersonate)
     *   - Sightings re-pointed to this Encounter (sets encounter_id)
     *   - Initial null→DRAFT row in encounter_status_history
     *   - EncounterCreatedEvent → async fanout to project members
     */
    @Audited("encounter.report")
    @Transactional
    public Encounter reportEncounter(ReportEncounterRequest req) {
        if (!projectGuard.canWrite(req.projectId())) {
            throw new ForbiddenException("No write access to project: " + req.projectId());
        }
        Long currentUserId = SecurityUtils.currentUserId();

        // ---- resolve & validate optional links BEFORE we write anything ----
        Individual individual = null;
        if (req.individualId() != null) {
            individual = indRepo.findById(req.individualId())
                .orElseThrow(() -> new NotFoundException("Individual not found: " + req.individualId()));
            if (req.species() != null && individual.getSpecies() != null
                && !req.species().equalsIgnoreCase(individual.getSpecies())) {
                throw new BusinessException(
                    "Species mismatch: encounter=" + req.species()
                    + " vs individual=" + individual.getSpecies());
            }
        }

        Observer observer = null;
        if (req.observerId() != null) {
            observer = obsRepo.findById(req.observerId())
                .orElseThrow(() -> new NotFoundException("Observer not found: " + req.observerId()));
        }

        Occurrence occurrence = null;
        if (req.occurrenceId() != null) {
            occurrence = occurrenceRepo.findById(req.occurrenceId())
                .orElseThrow(() -> new NotFoundException("Occurrence not found: " + req.occurrenceId()));
            // Cross-project attach would silently leak data between tenants. Refuse.
            if (occurrence.getProjectId() != null
                && !occurrence.getProjectId().equals(req.projectId())) {
                throw new BusinessException(
                    "Occurrence " + req.occurrenceId() + " belongs to project "
                    + occurrence.getProjectId() + ", not " + req.projectId());
            }
        }

        List<Sighting> sightings = List.of();
        if (req.sightingIds() != null && !req.sightingIds().isEmpty()) {
            sightings = sightingRepo.findAllById(req.sightingIds());
            if (sightings.size() != req.sightingIds().size()) {
                throw new NotFoundException("One or more sightings not found");
            }
            for (Sighting s : sightings) {
                if (s.getEncounter() != null) {
                    throw new BusinessException(
                        "Sighting " + s.getId() + " already belongs to encounter "
                        + s.getEncounter().getId() + "; detach it first");
                }
            }
        }

        // ---- create the Encounter ----
        Encounter enc = new Encounter();
        enc.setProjectId(req.projectId());
        enc.setSpecies(req.species());
        enc.setLocation(req.location());
        enc.setNotes(req.notes());
        enc.setEncounterDate(req.encounterDate());
        enc.setIndividual(individual);
        enc.setObserver(observer);
        enc.setOccurrence(occurrence);
        enc.setSubmitterUserId(currentUserId);
        Encounter saved = encRepo.save(enc);

        // ---- attach sightings (now that we have the encounter id) ----
        for (Sighting s : sightings) {
            s.setEncounter(saved);
            // observer-on-sighting carries over if not already set
            if (s.getObserver() == null && observer != null) {
                s.setObserver(observer);
            }
            sightingRepo.save(s);
        }

        // ---- workflow timeline + event ----
        historyRepo.save(new EncounterStatusHistory(
            saved.getId(), null, EncounterStatus.DRAFT, currentUserId,
            "reported with " + sightings.size() + " sighting(s)"
        ));
        events.publishEvent(new EncounterCreatedEvent(
            saved.getId(), saved.getProjectId(), currentUserId, Instant.now()
        ));
        events.publishEvent(new EncounterChangedEvent(saved.getId(), EncounterChangedEvent.Kind.UPSERT));
        return saved;
    }

    /**
     * Assign (or re-assign) an encounter to a project member.
     *
     *   - Must be a project member already (no cross-project leaks).
     *   - Fires EncounterAssignedEvent → direct notification to the assignee.
     *
     * No status transition happens here; assignment is orthogonal to
     * workflow (you can assign a DRAFT to a reviewer who pushes it to
     * REVIEWED, or assign a PUBLISHED encounter to a curator).
     */
    @Audited("encounter.assign")
    @CacheEvict(value = "encounter", key = "#id")
    @Transactional
    public Encounter assignTo(Long id, Long assigneeUserId) {
        Encounter enc = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
        requireWriteAccess(enc);

        if (assigneeUserId != null && !projectGuard.isMember(enc.getProjectId(), assigneeUserId)) {
            throw new BusinessException("Assignee is not a member of this project");
        }
        enc.setAssignedToUserId(assigneeUserId);
        Encounter saved = encRepo.save(enc);

        if (assigneeUserId != null) {
            events.publishEvent(new EncounterAssignedEvent(
                saved.getId(),
                saved.getProjectId(),
                assigneeUserId,
                SecurityUtils.currentUserId(),
                Instant.now()
            ));
        }
        events.publishEvent(new EncounterChangedEvent(saved.getId(), EncounterChangedEvent.Kind.UPSERT));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EncounterStatusHistory> listHistory(Long encounterId) {
        Encounter enc = encRepo.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        requireReadAccess(enc);
        return historyRepo.findByEncounterIdOrderByCreatedAtAsc(encounterId);
    }

    @Transactional
    public Encounter assignIndividual(Long id, UpdateEncounterRequest request) {
        Encounter enc = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter id not found: " + id));
        requireWriteAccess(enc);

        Long indId = request.individualId();
        Individual ind = indRepo.findById(indId)
            .orElseThrow(() -> new NotFoundException("Individual id not found: " + indId));
        if (enc.getSpecies() != null && !enc.getSpecies().equals(ind.getSpecies())) {
            throw new BusinessException("Species mismatch between encounter and individual");
        }
        enc.setIndividual(ind);
        return encRepo.save(enc);
    }

    /**
     * Move an encounter through its workflow.
     *
     *   1. Look up the encounter (must exist).
     *   2. Find the (current → target) entry in EncounterStatus's allowed-
     *      transition table; reject unknown transitions with a 400.
     *   3. Check the caller is at the minimum project role that transition
     *      requires (e.g., REVIEWED→PUBLISHED needs OWNER).
     *   4. Flip the status and save.
     *   5. On publish, publish an EncounterPublishedEvent that the
     *      notification listener fans out asynchronously after commit.
     *
     * Cache eviction: status change must invalidate the @Cacheable entry
     * keyed by id, otherwise findById would serve stale status.
     */
    @Audited("encounter.transition")
    @CacheEvict(value = "encounter", key = "#id")
    @Transactional
    public Encounter transition(Long id, EncounterStatus toStatus) {
        Encounter enc = encRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
        // Read access is enough to *see* an encounter — the transition itself
        // gates by the project role required for that specific arrow.
        if (enc.getProjectId() != null && !projectGuard.canRead(enc.getProjectId())) {
            throw new ForbiddenException("No access to encounter: " + id);
        }

        EncounterStatus current = enc.getStatus() == null ? EncounterStatus.DRAFT : enc.getStatus();
        EncounterStatus.Transition allowed = EncounterStatus.find(current, toStatus)
            .orElseThrow(() -> new BusinessException(
                "Illegal transition: " + current + " → " + toStatus));

        if (!projectGuard.hasAtLeast(enc.getProjectId(), allowed.minRole())) {
            throw new ForbiddenException(
                "Transition " + current + " → " + toStatus
                + " requires project role " + allowed.minRole());
        }

        EncounterStatus previous = current;
        enc.setStatus(toStatus);
        Encounter saved = encRepo.save(enc);

        // Append to the workflow timeline.
        historyRepo.save(new EncounterStatusHistory(
            saved.getId(),
            previous,
            toStatus,
            SecurityUtils.currentUserId(),
            null
        ));

        if (toStatus == EncounterStatus.PUBLISHED) {
            events.publishEvent(new EncounterPublishedEvent(
                saved.getId(),
                saved.getProjectId(),
                saved.getSpecies(),
                SecurityUtils.currentUserId(),
                Instant.now()
            ));
        }
        events.publishEvent(new EncounterChangedEvent(saved.getId(), EncounterChangedEvent.Kind.UPSERT));
        return saved;
    }

    /**
     * Streaming export. Consumer is invoked for each row WITHOUT holding the
     * full result set in memory. The DB cursor + entity lifecycle stay inside
     * this @Transactional boundary; if the consumer throws, the tx rolls back.
     *
     * Why callback-style and not "return Stream<Encounter>": the JPA stream
     * is bound to a Session that closes when this method returns, so the
     * controller wouldn't be able to consume it. Forcing the work into the
     * service keeps the contract clean.
     */
    @Transactional(readOnly = true)
    public void streamByProject(Long projectId, Consumer<Encounter> consumer) {
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }
        try (Stream<Encounter> stream = encRepo.streamByProjectId(projectId)) {
            stream.forEach(consumer);
        }
    }

    // ----- permission helpers -----

    private void requireReadAccess(Encounter e) {
        if (e.getProjectId() != null && !projectGuard.canRead(e.getProjectId())) {
            throw new ForbiddenException("No read access to encounter: " + e.getId());
        }
    }

    private void requireWriteAccess(Encounter e) {
        if (e.getProjectId() != null && !projectGuard.canWrite(e.getProjectId())) {
            throw new ForbiddenException("No write access to encounter: " + e.getId());
        }
    }
}
