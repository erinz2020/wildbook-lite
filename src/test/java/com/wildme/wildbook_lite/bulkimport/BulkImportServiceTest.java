package com.wildme.wildbook_lite.bulkimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.wildme.wildbook_lite.auth.AppPrincipal;
import com.wildme.wildbook_lite.bulkimport.dto.BulkEncounterRow;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportRequest;
import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResult;
import com.wildme.wildbook_lite.bulkimport.dto.RowFailure;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.encounter.LivingStatus;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.entity.Observer;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;
import com.wildme.wildbook_lite.repository.IndividualRepository;
import com.wildme.wildbook_lite.repository.ObserverRepository;
import com.wildme.wildbook_lite.search.opensearch.EncounterChangedEvent;
import com.wildme.wildbook_lite.taxonomy.Taxonomy;
import com.wildme.wildbook_lite.taxonomy.TaxonomyRepository;

/**
 * Unit tests for {@link BulkImportService}.
 *
 * Mocking strategy:
 *   - All collaborators are Mockito @Mocks (no Spring context, no DB).
 *   - The SUT's @Lazy self-reference is handled by reflection in
 *     {@link #setUp()}: we set `self = svc` so the orchestrator's
 *     `self.processRow(...)` calls land back on the same instance.
 *     In production Spring sets `self` to a transactional proxy; in
 *     this unit test we don't care about the transaction, only that
 *     the call routes to the right method.
 *
 * SecurityContext:
 *   - importBatch() calls SecurityUtils.currentUserId() (which reads
 *     SecurityContextHolder). We set a principal in @BeforeEach and
 *     clear it in @AfterEach so tests don't leak auth state.
 */
@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock EncounterRepository encRepo;
    @Mock IndividualRepository indRepo;
    @Mock ObserverRepository observerRepo;
    @Mock TaxonomyRepository taxonomyRepo;
    @Mock ProjectGuard projectGuard;
    @Mock ApplicationEventPublisher events;

    private static final Long PROJECT_ID = 1L;
    private static final Long CURRENT_USER_ID = 42L;

    private BulkImportService svc;

    @BeforeEach
    void setUp() throws Exception {
        svc = new BulkImportService(
            /* self placeholder */ null,
            encRepo, indRepo, observerRepo, taxonomyRepo, projectGuard, events);

        // Wire self -> svc so the orchestrator's self.processRow() routes
        // back to the SUT. In Spring this is a proxy; here we just need
        // SOME non-null reference that resolves to the right instance.
        Field selfField = BulkImportService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(svc, svc);

        // Login as user 42.
        AppPrincipal principal = new AppPrincipal(
            CURRENT_USER_ID, "alice", "ignored", true, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- batch-level (importBatch) ----------

    @Nested
    @DisplayName("importBatch — orchestration")
    class ImportBatch {

        @Test
        @DisplayName("forbidden when user lacks project write access")
        void forbiddenWhenNoWriteAccess() {
            when(projectGuard.canWrite(PROJECT_ID)).thenReturn(false);

            assertThatThrownBy(() -> svc.importBatch(req(minimalRow("Humpback whale", null))))
                .isInstanceOf(ForbiddenException.class);

            verify(encRepo, never()).save(any());
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("happy path: single valid row creates one Encounter")
        void singleRowHappyPath() {
            allowWrite();
            when(encRepo.save(any(Encounter.class))).thenAnswer(inv -> {
                Encounter e = inv.getArgument(0);
                e.setId(100L);
                return e;
            });

            BulkImportResult result = svc.importBatch(req(
                minimalRow("Humpback whale", null)));

            assertThat(result.totalRows()).isEqualTo(1);
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.failureCount()).isZero();
            assertThat(result.created()).hasSize(1);
            assertThat(result.created().get(0).encounterId()).isEqualTo(100L);
            // EncounterChangedEvent fires AFTER the per-row commit
            ArgumentCaptor<EncounterChangedEvent> evt = ArgumentCaptor.forClass(EncounterChangedEvent.class);
            verify(events, times(1)).publishEvent(evt.capture());
            assertThat(evt.getValue().encounterId()).isEqualTo(100L);
            assertThat(evt.getValue().kind()).isEqualTo(EncounterChangedEvent.Kind.UPSERT);
        }

        @Test
        @DisplayName("partial failure: bad rows reported, good rows still committed")
        void partialFailure() {
            allowWrite();
            // Row 0 (bad: no species, no scientificName) and Row 2 (bad: no date) fail.
            // Row 1 (valid) succeeds.
            when(encRepo.save(any(Encounter.class))).thenAnswer(inv -> {
                Encounter e = inv.getArgument(0);
                e.setId(200L);
                return e;
            });

            BulkEncounterRow bad0 = new BulkEncounterRow(0, null, null, null,
                LocalDateTime.now(), null, null, null, null,
                null, null, null, null, null, null, null, null, null);
            BulkEncounterRow good = minimalRow("Humpback whale", null);
            BulkEncounterRow bad2 = new BulkEncounterRow(2, "Orca", null, null,
                /* missing encounterDate */ null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);

            BulkImportResult result = svc.importBatch(req(bad0, good, bad2));

            assertThat(result.totalRows()).isEqualTo(3);
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.failureCount()).isEqualTo(2);
            assertThat(result.failed()).extracting(RowFailure::rowIndex)
                .containsExactlyInAnyOrder(0, 2);
            assertThat(result.failed()).extracting(RowFailure::errorCode)
                .containsOnly(RowFailure.CODE_VALIDATION);
            // Only the GOOD row should have published its event.
            verify(events, times(1)).publishEvent(any(EncounterChangedEvent.class));
        }

        @Test
        @DisplayName("auto-create Taxonomy: new scientificName creates the row and flag is set")
        void autoCreatesTaxonomy() {
            allowWrite();
            when(taxonomyRepo.findByScientificNameIgnoreCase("Megaptera novaeangliae"))
                .thenReturn(Optional.empty());
            when(taxonomyRepo.save(any(Taxonomy.class))).thenAnswer(inv -> {
                Taxonomy t = inv.getArgument(0);
                t.setId(7L);
                return t;
            });
            when(encRepo.save(any(Encounter.class))).thenAnswer(inv -> {
                Encounter e = inv.getArgument(0);
                e.setId(101L);
                return e;
            });

            BulkEncounterRow row = new BulkEncounterRow(0,
                "Humpback whale",
                "Megaptera novaeangliae",
                "Humpback whale",
                LocalDateTime.now(),
                null, null, null, null,
                null, null, null, null, null,
                null,
                null, null, null);

            BulkImportResult result = svc.importBatch(req(row));

            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.taxonomyAutoCreatedCount()).isEqualTo(1);
            assertThat(result.created().get(0).taxonomyId()).isEqualTo(7L);
            assertThat(result.created().get(0).taxonomyCreated()).isTrue();
            // Verify the Taxonomy row was built with the derived genus.
            ArgumentCaptor<Taxonomy> tx = ArgumentCaptor.forClass(Taxonomy.class);
            verify(taxonomyRepo, times(1)).save(tx.capture());
            assertThat(tx.getValue().getGenus()).isEqualTo("Megaptera");
            assertThat(tx.getValue().getSpecificEpithet()).isEqualTo("novaeangliae");
            // Encounter.species denorm rewritten to canonical name.
            ArgumentCaptor<Encounter> enc = ArgumentCaptor.forClass(Encounter.class);
            verify(encRepo).save(enc.capture());
            assertThat(enc.getValue().getSpecies()).isEqualTo("Megaptera novaeangliae");
        }

        @Test
        @DisplayName("auto-create Observer: new observerName creates the row and flag is set")
        void autoCreatesObserver() {
            allowWrite();
            when(observerRepo.findFirstByNameIgnoreCase("Jane Doe")).thenReturn(Optional.empty());
            when(observerRepo.save(any(Observer.class))).thenAnswer(inv -> {
                Observer o = inv.getArgument(0);
                o.setId(3L);
                return o;
            });
            when(encRepo.save(any(Encounter.class))).thenAnswer(inv -> {
                Encounter e = inv.getArgument(0);
                e.setId(102L);
                return e;
            });

            BulkEncounterRow row = new BulkEncounterRow(0,
                "Humpback whale", null, null,
                LocalDateTime.now(),
                null, null, null, null,
                null, null, null, null, null,
                null,
                "Jane Doe", "jane@x.com", "PWF");

            BulkImportResult result = svc.importBatch(req(row));

            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.observerAutoCreatedCount()).isEqualTo(1);
            assertThat(result.created().get(0).observerId()).isEqualTo(3L);
            assertThat(result.created().get(0).observerCreated()).isTrue();
            // Observer fields propagated from the row.
            ArgumentCaptor<Observer> oc = ArgumentCaptor.forClass(Observer.class);
            verify(observerRepo).save(oc.capture());
            assertThat(oc.getValue().getName()).isEqualTo("Jane Doe");
            assertThat(oc.getValue().getEmail()).isEqualTo("jane@x.com");
            assertThat(oc.getValue().getOrganization()).isEqualTo("PWF");
        }

        @Test
        @DisplayName("autoCreateTaxonomy=false + missing taxonomy → TAXONOMY_NOT_FOUND")
        void taxonomyMissingWhenAutoCreateOff() {
            allowWrite();
            when(taxonomyRepo.findByScientificNameIgnoreCase("Unknown sp")).thenReturn(Optional.empty());

            BulkEncounterRow row = new BulkEncounterRow(0,
                null, "Unknown sp", null,
                LocalDateTime.now(),
                null, null, null, null,
                null, null, null, null, null,
                null,
                null, null, null);

            // autoCreateTaxonomy = false, autoCreateObserver = default (true)
            BulkImportRequest request = new BulkImportRequest(PROJECT_ID, List.of(row), false, null);
            BulkImportResult result = svc.importBatch(request);

            assertThat(result.successCount()).isZero();
            assertThat(result.failed()).hasSize(1);
            assertThat(result.failed().get(0).errorCode())
                .isEqualTo(RowFailure.CODE_TAXONOMY_MISSING);
            verify(taxonomyRepo, never()).save(any());
            verify(encRepo, never()).save(any());
        }
    }

    // ---------- per-row (processRow) ----------

    @Nested
    @DisplayName("processRow — single-row validation")
    class ProcessRow {

        @Test
        @DisplayName("missing both species and scientificName → CODE_VALIDATION")
        void requiresSomeSpecies() {
            allowWrite();
            BulkEncounterRow row = new BulkEncounterRow(5, null, null, null,
                LocalDateTime.now(), null, null, null, null,
                null, null, null, null, null, null, null, null, null);

            BulkImportResult r = svc.importBatch(req(row));

            assertThat(r.failed()).singleElement()
                .satisfies(f -> {
                    assertThat(f.rowIndex()).isEqualTo(5);
                    assertThat(f.errorCode()).isEqualTo(RowFailure.CODE_VALIDATION);
                    assertThat(f.errorMessage()).contains("species");
                });
        }

        @Test
        @DisplayName("partial GPS (lat without lng) → CODE_VALIDATION")
        void rejectsPartialGps() {
            allowWrite();
            BulkEncounterRow row = new BulkEncounterRow(0,
                "Humpback whale", null, null,
                LocalDateTime.now(),
                null, null, /*lat*/ 20.9, /*lng*/ null,
                null, null, null, null, null,
                null,
                null, null, null);

            BulkImportResult r = svc.importBatch(req(row));

            assertThat(r.failureCount()).isEqualTo(1);
            assertThat(r.failed().get(0).errorCode()).isEqualTo(RowFailure.CODE_VALIDATION);
            assertThat(r.failed().get(0).errorMessage()).contains("decimalLatitude");
        }

        @Test
        @DisplayName("individualNickname not found → CODE_INDIVIDUAL_NOT_FOUND")
        void individualMissing() {
            allowWrite();
            when(indRepo.findAll()).thenReturn(List.of()); // nobody with that nickname

            BulkEncounterRow row = new BulkEncounterRow(0,
                "Humpback whale", null, null,
                LocalDateTime.now(),
                null, null, null, null,
                null, null, null, null, null,
                "Salt",   // nickname doesn't exist
                null, null, null);

            BulkImportResult r = svc.importBatch(req(row));

            assertThat(r.failureCount()).isEqualTo(1);
            assertThat(r.failed().get(0).errorCode()).isEqualTo(RowFailure.CODE_INDIVIDUAL_MISSING);
        }

        @Test
        @DisplayName("individual exists but species disagrees → CODE_SPECIES_MISMATCH")
        void speciesMismatch() {
            allowWrite();
            Individual ind = new Individual();
            ind.setId(11L);
            ind.setNickname("Salt");
            ind.setSpecies("Orca");                     // different from row species
            when(indRepo.findAll()).thenReturn(List.of(ind));

            BulkEncounterRow row = new BulkEncounterRow(0,
                "Humpback whale", null, null,
                LocalDateTime.now(),
                null, null, null, null,
                null, null, null, null, null,
                "Salt",
                null, null, null);

            BulkImportResult r = svc.importBatch(req(row));

            assertThat(r.failureCount()).isEqualTo(1);
            assertThat(r.failed().get(0).errorCode()).isEqualTo(RowFailure.CODE_SPECIES_MISMATCH);
            verify(encRepo, never()).save(any());
        }

        @Test
        @DisplayName("row with all optional biological fields persists them all")
        void persistsBiologicalFields() {
            allowWrite();
            ArgumentCaptor<Encounter> enc = ArgumentCaptor.forClass(Encounter.class);
            when(encRepo.save(enc.capture())).thenAnswer(inv -> {
                Encounter e = inv.getArgument(0);
                e.setId(500L);
                return e;
            });

            BulkEncounterRow row = new BulkEncounterRow(0,
                "Humpback whale", null, null,
                LocalDateTime.of(2026, 6, 11, 8, 30),
                "Maui north shore", "USA/HI/Maui", 20.9, -156.5,
                "calf and mother",
                "adult", "feeding", LivingStatus.ALIVE,
                "{\"sea_state\":2}",
                null,
                null, null, null);

            BulkImportResult r = svc.importBatch(req(row));

            assertThat(r.successCount()).isEqualTo(1);
            Encounter saved = enc.getValue();
            assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(saved.getSubmitterUserId()).isEqualTo(CURRENT_USER_ID);
            assertThat(saved.getLocation()).isEqualTo("Maui north shore");
            assertThat(saved.getLocationId()).isEqualTo("USA/HI/Maui");
            assertThat(saved.getDecimalLatitude()).isEqualTo(20.9);
            assertThat(saved.getDecimalLongitude()).isEqualTo(-156.5);
            assertThat(saved.getLifeStage()).isEqualTo("adult");
            assertThat(saved.getBehavior()).isEqualTo("feeding");
            assertThat(saved.getLivingStatus()).isEqualTo(LivingStatus.ALIVE);
            assertThat(saved.getDynamicProperties()).contains("sea_state");
        }
    }

    // ---------- helpers ----------

    private void allowWrite() {
        when(projectGuard.canWrite(PROJECT_ID)).thenReturn(true);
    }

    private BulkImportRequest req(BulkEncounterRow... rows) {
        return new BulkImportRequest(PROJECT_ID, List.of(rows), null, null);
    }

    /** Smallest BulkEncounterRow that is valid for import. */
    private BulkEncounterRow minimalRow(String species, String scientificName) {
        return new BulkEncounterRow(
            0, species, scientificName, null,
            LocalDateTime.of(2026, 6, 11, 8, 30),
            null, null, null, null,
            null, null, null, null, null,
            null,
            null, null, null);
    }
}
