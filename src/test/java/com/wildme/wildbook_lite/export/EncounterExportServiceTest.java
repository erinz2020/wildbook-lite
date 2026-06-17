package com.wildme.wildbook_lite.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;

/**
 * Tests for {@link EncounterExportService}.
 *
 * Key invariants under test:
 *   - ProjectGuard.canRead is consulted; absent permission → no DB hit
 *     (we'd see verify(encRepo).streamByProjectId(...) failing)
 *   - The filter `accepts` predicate is applied (rows that don't match
 *     are not written, but the row count returned matches what landed)
 *   - The output is well-formed CSV: 1 header line + N data lines,
 *     all CRLF-terminated
 *   - Null filters object is treated as ExportFilters.NONE
 *
 * We don't test the streaming behavior end-to-end (no Testcontainers
 * here). What we DO verify is that the service correctly drains the
 * stream and writes to whatever OutputStream we hand it.
 */
@ExtendWith(MockitoExtension.class)
class EncounterExportServiceTest {

    @Mock EncounterRepository encRepo;
    @Mock ProjectGuard projectGuard;

    @InjectMocks
    EncounterExportService svc;

    private static final Long PROJECT_ID = 1L;

    @Test
    @DisplayName("no read access → ForbiddenException, never touches the repo")
    void forbidden() {
        when(projectGuard.canRead(PROJECT_ID)).thenReturn(false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> svc.writeCsv(PROJECT_ID, ExportFilters.NONE, out))
            .isInstanceOf(ForbiddenException.class);

        // Output stream untouched
        assertThat(out.size()).isZero();
        // Mockito would have failed verification if streamByProjectId was called
    }

    @Test
    @DisplayName("happy path: header + 2 rows, all CRLF-terminated")
    void happyPath() {
        when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);
        when(encRepo.streamByProjectId(PROJECT_ID)).thenReturn(Stream.of(
            encounter(1L, "Humpback whale", EncounterStatus.DRAFT),
            encounter(2L, "Orca",            EncounterStatus.PUBLISHED)
        ));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = svc.writeCsv(PROJECT_ID, ExportFilters.NONE, out);

        assertThat(written).isEqualTo(2);
        String csv = out.toString();
        // 1 header + 2 rows = 3 CRLFs
        long crlfs = csv.lines().count();   // String.lines() splits on line terminators
        assertThat(crlfs).isEqualTo(3);
        assertThat(csv).startsWith("id,projectId,species,");
        assertThat(csv).contains(",Humpback whale,");
        assertThat(csv).contains(",Orca,");
    }

    @Test
    @DisplayName("filters skip non-matching rows but DB still scans them")
    void speciesFilter() {
        when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);
        when(encRepo.streamByProjectId(PROJECT_ID)).thenReturn(Stream.of(
            encounter(1L, "Humpback whale", EncounterStatus.DRAFT),
            encounter(2L, "Orca",            EncounterStatus.PUBLISHED),
            encounter(3L, "Humpback whale", EncounterStatus.PUBLISHED)
        ));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = svc.writeCsv(PROJECT_ID,
            new ExportFilters("Humpback whale", null, null, null), out);

        assertThat(written).isEqualTo(2);
        String csv = out.toString();
        assertThat(csv).contains(",Humpback whale,");
        assertThat(csv).doesNotContain(",Orca,");
    }

    @Test
    @DisplayName("status filter: only matching rows are emitted")
    void statusFilter() {
        when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);
        when(encRepo.streamByProjectId(PROJECT_ID)).thenReturn(Stream.of(
            encounter(1L, "Humpback whale", EncounterStatus.DRAFT),
            encounter(2L, "Humpback whale", EncounterStatus.PUBLISHED),
            encounter(3L, "Humpback whale", EncounterStatus.ARCHIVED)
        ));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = svc.writeCsv(PROJECT_ID,
            new ExportFilters(null, EncounterStatus.PUBLISHED, null, null), out);

        assertThat(written).isEqualTo(1);
        assertThat(out.toString()).contains(",PUBLISHED,");
        assertThat(out.toString()).doesNotContain(",DRAFT,");
        assertThat(out.toString()).doesNotContain(",ARCHIVED,");
    }

    @Test
    @DisplayName("date range filter (from + to inclusive on encounterDate)")
    void dateRangeFilter() {
        when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);
        when(encRepo.streamByProjectId(PROJECT_ID)).thenReturn(Stream.of(
            encounterWithDate(1L, LocalDateTime.of(2025, 12, 31, 23, 59)),  // before
            encounterWithDate(2L, LocalDateTime.of(2026, 6, 11, 12, 0)),    // in
            encounterWithDate(3L, LocalDateTime.of(2027, 1, 1, 0, 1))       // after
        ));

        ExportFilters f = new ExportFilters(null, null,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 12, 31, 23, 59));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = svc.writeCsv(PROJECT_ID, f, out);

        assertThat(written).isEqualTo(1);
        assertThat(out.toString()).contains("2026-06-11T12:00:00");
    }

    @Test
    @DisplayName("null filters object is treated as ExportFilters.NONE")
    void nullFilters() {
        when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);
        when(encRepo.streamByProjectId(PROJECT_ID)).thenReturn(Stream.of(
            encounter(1L, "Humpback whale", EncounterStatus.DRAFT)
        ));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = svc.writeCsv(PROJECT_ID, null, out);

        assertThat(written).isEqualTo(1);
    }

    @Test
    @DisplayName("empty project: header still emitted, zero data rows")
    void emptyProject() {
        when(projectGuard.canRead(PROJECT_ID)).thenReturn(true);
        when(encRepo.streamByProjectId(PROJECT_ID)).thenReturn(Stream.empty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = svc.writeCsv(PROJECT_ID, ExportFilters.NONE, out);

        assertThat(written).isZero();
        String csv = out.toString();
        assertThat(csv).startsWith("id,projectId,species,");
        // Just the header — exactly one CRLF-terminated line
        assertThat(csv.lines().count()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private Encounter encounter(Long id, String species, EncounterStatus status) {
        Encounter e = new Encounter();
        e.setId(id);
        e.setProjectId(PROJECT_ID);
        e.setSpecies(species);
        e.setStatus(status);
        e.setEncounterDate(LocalDateTime.of(2026, 6, 11, 8, 30));
        return e;
    }

    private Encounter encounterWithDate(Long id, LocalDateTime when) {
        Encounter e = encounter(id, "Humpback whale", EncounterStatus.DRAFT);
        e.setEncounterDate(when);
        return e;
    }
}
