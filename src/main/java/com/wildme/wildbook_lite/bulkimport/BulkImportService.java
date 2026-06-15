package com.wildme.wildbook_lite.bulkimport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.bulkimport.dto.BulkEncounterRow;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResult;
import com.wildme.wildbook_lite.bulkimport.dto.RowFailure;
import com.wildme.wildbook_lite.bulkimport.dto.RowSuccess;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.entity.Observer;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.repository.IndividualRepository;
import com.wildme.wildbook_lite.repository.ObserverRepository;
import com.wildme.wildbook_lite.search.opensearch.EncounterChangedEvent;
import com.wildme.wildbook_lite.taxonomy.Taxonomy;
import com.wildme.wildbook_lite.taxonomy.TaxonomyRepository;

/**
 * Bulk import of Encounters from a frontend-parsed xlsx.
 *
 * Pipeline:
 *   1. {@link #importBatch(BulkImportRequest)} runs ONCE on the request
 *      thread — checks project-level write access, then iterates the
 *      rows.
 *   2. For each row it calls `self.processRow(...)` which is
 *      @Transactional(REQUIRES_NEW) — so a bad row only rolls back
 *      itself, leaving its successful siblings committed.
 *   3. Successful rows publish EncounterChangedEvent (UPSERT) so the
 *      OS index resyncs via the existing AFTER_COMMIT @Async listener.
 *
 * Why @Lazy self-injection (interview gold, repeated from
 * EncounterBulkService for posterity):
 *   - Spring AOP is proxy-based. Calling `this.processRow(...)` from
 *     within the same bean hits the raw object — @Transactional advice
 *     never fires. Routing through `self` (which IS the proxy) makes
 *     the per-row tx actually start.
 *   - @Lazy breaks the otherwise-circular "I depend on myself" graph
 *     that would crash bean creation.
 *
 * Why the project-level permission check is at the top, not per row:
 *   - All rows share the same projectId (the DTO enforces it).
 *   - 1000 rows × ProjectGuard.canWrite() = 1000 DB lookups just to
 *     learn "yes, this user can still write here". The user's role
 *     can't change mid-batch in any realistic scenario; the
 *     transaction-bypass would be a clear engineering trade-off
 *     anyway.
 *
 * Find-or-create policy:
 *   - Taxonomy: looked up by scientificName (case-insensitive). When
 *     autoCreateTaxonomy=true, a missing row is created with the
 *     supplied commonName.
 *   - Observer: looked up by name (case-insensitive). Same policy.
 *   - Individual: lookup by nickname ONLY. NEVER auto-created —
 *     registering an animal is a deliberate, supervised act.
 */
@Service
public class BulkImportService {

    private static final Logger log = LoggerFactory.getLogger(BulkImportService.class);

    private final BulkImportService self;
    private final EncounterRepository encRepo;
    private final IndividualRepository indRepo;
    private final ObserverRepository observerRepo;
    private final TaxonomyRepository taxonomyRepo;
    private final ProjectGuard projectGuard;
    private final ApplicationEventPublisher events;

    public BulkImportService(@Lazy BulkImportService self,
                             EncounterRepository encRepo,
                             IndividualRepository indRepo,
                             ObserverRepository observerRepo,
                             TaxonomyRepository taxonomyRepo,
                             ProjectGuard projectGuard,
                             ApplicationEventPublisher events) {
        this.self = self;
        this.encRepo = encRepo;
        this.indRepo = indRepo;
        this.observerRepo = observerRepo;
        this.taxonomyRepo = taxonomyRepo;
        this.projectGuard = projectGuard;
        this.events = events;
    }

    @Audited("encounter.bulkImport")
    public BulkImportResult importBatch(BulkImportRequest req) {
        if (!projectGuard.canWrite(req.projectId())) {
            throw new ForbiddenException("No write access to project: " + req.projectId());
        }

        List<BulkEncounterRow> rows = req.rows();
        List<RowSuccess> created = new ArrayList<>(rows.size());
        List<RowFailure> failed = new ArrayList<>();
        int taxonomyCreated = 0;
        int observerCreated = 0;

        Long submitterId = SecurityUtils.currentUserId();

        for (int i = 0; i < rows.size(); i++) {
            BulkEncounterRow row = rows.get(i);
            // Use client-supplied rowIndex when present, else fall back
            // to the position in the request — gives the UI a way to
            // mark exactly which row in their spreadsheet broke.
            int rowIndex = row.rowIndex() != null ? row.rowIndex() : i;

            try {
                RowSuccess s = self.processRow(req.projectId(), submitterId, rowIndex, row,
                                               Boolean.TRUE.equals(req.autoCreateTaxonomy()),
                                               Boolean.TRUE.equals(req.autoCreateObserver()));
                created.add(s);
                if (s.taxonomyCreated()) taxonomyCreated++;
                if (s.observerCreated()) observerCreated++;
                // Index resync fires OUTSIDE the per-row tx (after it
                // committed) — so AFTER_COMMIT listeners see real data.
                events.publishEvent(new EncounterChangedEvent(
                    s.encounterId(), EncounterChangedEvent.Kind.UPSERT));
            } catch (BusinessException be) {
                failed.add(new RowFailure(rowIndex, classifyBusiness(be),
                                          be.getMessage()));
            } catch (NotFoundException nfe) {
                failed.add(new RowFailure(rowIndex, classifyNotFound(nfe),
                                          nfe.getMessage()));
            } catch (Exception e) {
                // Internal failure — we keep going so the rest of the
                // batch can still land. Detail is logged server-side;
                // client gets a generic message to avoid leaking SQL.
                log.warn("[bulkImport] row {} crashed: {}", rowIndex, e.toString(), e);
                failed.add(new RowFailure(rowIndex, RowFailure.CODE_INTERNAL,
                                          e.getClass().getSimpleName()));
            }
        }

        return new BulkImportResult(
            rows.size(),
            created.size(),
            failed.size(),
            taxonomyCreated,
            observerCreated,
            created,
            failed
        );
    }

    /**
     * Single-row processor. MUST be public AND called via `self.` so
     * the REQUIRES_NEW proxy advice fires.
     *
     * Validation order is "fail-fast on cheap checks first" so we waste
     * the least DB work on bad rows.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowSuccess processRow(Long projectId,
                                 Long submitterUserId,
                                 int rowIndex,
                                 BulkEncounterRow row,
                                 boolean autoCreateTaxonomy,
                                 boolean autoCreateObserver) {
        // 1. cheap structural validation
        if ((row.species() == null || row.species().isBlank())
            && (row.scientificName() == null || row.scientificName().isBlank())) {
            throw new BusinessException("species or scientificName is required");
        }
        if (row.encounterDate() == null) {
            throw new BusinessException("encounterDate is required");
        }
        boolean hasLat = row.decimalLatitude() != null;
        boolean hasLng = row.decimalLongitude() != null;
        if (hasLat ^ hasLng) {
            throw new BusinessException("decimalLatitude and decimalLongitude must both be set or both be null");
        }

        // 2. taxonomy resolve (optional)
        Taxonomy taxonomy = null;
        boolean taxCreated = false;
        if (row.scientificName() != null && !row.scientificName().isBlank()) {
            taxonomy = taxonomyRepo.findByScientificNameIgnoreCase(row.scientificName())
                .orElse(null);
            if (taxonomy == null) {
                if (!autoCreateTaxonomy) {
                    throw new BusinessException(
                        "Taxonomy not found for scientificName='" + row.scientificName()
                        + "' (and autoCreateTaxonomy=false)");
                }
                taxonomy = createTaxonomy(row.scientificName(), row.commonName());
                taxCreated = true;
            }
        }

        // Denormalized species: prefer the canonical name when we have
        // a Taxonomy, else use the free-text. Encounter.species drives
        // every species filter, so this must always be set.
        String denormSpecies = taxonomy != null
            ? taxonomy.getScientificName()
            : row.species();

        // 3. individual lookup (must exist if requested)
        Individual individual = null;
        if (row.individualNickname() != null && !row.individualNickname().isBlank()) {
            individual = findIndividualByNickname(row.individualNickname());
            if (individual == null) {
                throw new NotFoundException(
                    "Individual not found: nickname='" + row.individualNickname() + "'");
            }
            if (individual.getSpecies() != null && denormSpecies != null
                && !individual.getSpecies().equalsIgnoreCase(denormSpecies)) {
                throw new BusinessException(
                    "Species mismatch: row='" + denormSpecies
                    + "' vs individual='" + individual.getSpecies() + "'");
            }
        }

        // 4. observer find-or-create
        Observer observer = null;
        boolean obsCreated = false;
        if (row.observerName() != null && !row.observerName().isBlank()) {
            observer = observerRepo.findFirstByNameIgnoreCase(row.observerName()).orElse(null);
            if (observer == null) {
                if (!autoCreateObserver) {
                    throw new BusinessException(
                        "Observer not found: name='" + row.observerName()
                        + "' (and autoCreateObserver=false)");
                }
                observer = createObserver(row.observerName(), row.observerEmail(),
                                          row.observerOrganization());
                obsCreated = true;
            }
        }

        // 5. build + save the Encounter (always DRAFT; transitions are a
        //    separate, deliberate workflow — not a bulk import responsibility)
        Encounter e = new Encounter();
        e.setProjectId(projectId);
        e.setSubmitterUserId(submitterUserId);
        e.setSpecies(denormSpecies);
        e.setTaxonomy(taxonomy);
        e.setLocation(row.location());
        e.setLocationId(row.locationId());
        e.setDecimalLatitude(row.decimalLatitude());
        e.setDecimalLongitude(row.decimalLongitude());
        e.setEncounterDate(row.encounterDate());
        e.setNotes(row.notes());
        e.setLifeStage(row.lifeStage());
        e.setBehavior(row.behavior());
        if (row.livingStatus() != null) e.setLivingStatus(row.livingStatus());
        e.setDynamicProperties(row.dynamicProperties());
        e.setIndividual(individual);
        e.setObserver(observer);
        Encounter saved = encRepo.save(e);

        return new RowSuccess(
            rowIndex,
            saved.getId(),
            taxonomy == null ? null : taxonomy.getId(),
            taxCreated,
            observer == null ? null : observer.getId(),
            obsCreated,
            individual == null ? null : individual.getId()
        );
    }

    // ----- helpers -----

    private Taxonomy createTaxonomy(String scientificName, String commonName) {
        Taxonomy t = new Taxonomy();
        t.setScientificName(scientificName.trim());
        // Derive genus / specific epithet from "Genus species" if possible.
        int sp = scientificName.indexOf(' ');
        if (sp > 0) {
            t.setGenus(scientificName.substring(0, sp).trim());
            if (sp + 1 < scientificName.length()) {
                t.setSpecificEpithet(scientificName.substring(sp + 1).trim());
            }
        } else {
            t.setGenus(scientificName.trim());
        }
        if (commonName != null && !commonName.isBlank()) {
            t.setCommonNames(commonName.trim());
        }
        t.setCreatedAt(LocalDateTime.now());
        return taxonomyRepo.save(t);
    }

    private Observer createObserver(String name, String email, String organization) {
        Observer o = new Observer();
        o.setName(name.trim());
        if (email != null && !email.isBlank())               o.setEmail(email.trim());
        if (organization != null && !organization.isBlank()) o.setOrganization(organization.trim());
        return observerRepo.save(o);
    }

    /**
     * Find an Individual by its nickname. Currently does a stream scan
     * because Individual nicknames aren't indexed / unique-constrained.
     * Fine for the dataset sizes we're modeling; if it ever shows up
     * on a flame graph, add `Optional<Individual> findFirstByNicknameIgnoreCase(String)`
     * to IndividualRepository.
     */
    private Individual findIndividualByNickname(String nickname) {
        String needle = nickname.trim();
        return indRepo.findAll().stream()
            .filter(i -> needle.equalsIgnoreCase(i.getNickname()))
            .findFirst()
            .orElse(null);
    }

    private String classifyBusiness(BusinessException be) {
        String msg = be.getMessage() == null ? "" : be.getMessage().toLowerCase();
        if (msg.contains("species mismatch")) return RowFailure.CODE_SPECIES_MISMATCH;
        if (msg.contains("taxonomy"))         return RowFailure.CODE_TAXONOMY_MISSING;
        return RowFailure.CODE_VALIDATION;
    }

    private String classifyNotFound(NotFoundException nfe) {
        String msg = nfe.getMessage() == null ? "" : nfe.getMessage().toLowerCase();
        if (msg.contains("individual")) return RowFailure.CODE_INDIVIDUAL_MISSING;
        if (msg.contains("taxonomy"))   return RowFailure.CODE_TAXONOMY_MISSING;
        return RowFailure.CODE_VALIDATION;
    }
}
