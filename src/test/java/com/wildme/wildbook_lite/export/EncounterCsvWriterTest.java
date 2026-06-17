package com.wildme.wildbook_lite.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.io.Writer;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.encounter.LivingStatus;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.entity.Individual;
import com.wildme.wildbook_lite.entity.Observer;
import com.wildme.wildbook_lite.taxonomy.Taxonomy;

/**
 * Pure unit tests for the CSV writer. No mocks, no Spring, no I/O —
 * the writer takes a {@link Writer}, so we hand it a {@link StringWriter}
 * and assert on the produced text directly.
 *
 * Coverage focus: RFC 4180 quoting edge cases (the hand-rolled bit
 * with the highest risk), null handling, and column order stability.
 */
class EncounterCsvWriterTest {

    @Nested
    @DisplayName("escape (RFC 4180 quoting)")
    class Escape {

        @Test
        @DisplayName("plain text is returned unchanged (fast path)")
        void plainText() {
            assertThat(EncounterCsvWriter.escape("hello")).isEqualTo("hello");
        }

        @Test
        @DisplayName("null becomes empty string")
        void nullValue() {
            assertThat(EncounterCsvWriter.escape(null)).isEqualTo("");
        }

        @Test
        @DisplayName("empty string is returned unchanged")
        void emptyValue() {
            assertThat(EncounterCsvWriter.escape("")).isEqualTo("");
        }

        @Test
        @DisplayName("comma triggers quoting")
        void commaTriggersQuotes() {
            assertThat(EncounterCsvWriter.escape("Maui, HI")).isEqualTo("\"Maui, HI\"");
        }

        @Test
        @DisplayName("embedded double quote is doubled and field is wrapped")
        void embeddedQuote() {
            // Whitman said: "I contain multitudes"
            // becomes:      "Whitman said: ""I contain multitudes"""
            assertThat(EncounterCsvWriter.escape("Whitman said: \"I contain multitudes\""))
                .isEqualTo("\"Whitman said: \"\"I contain multitudes\"\"\"");
        }

        @Test
        @DisplayName("CR / LF inside a field triggers quoting (preserves the newline)")
        void newlineInsideField() {
            assertThat(EncounterCsvWriter.escape("line1\nline2"))
                .isEqualTo("\"line1\nline2\"");
            assertThat(EncounterCsvWriter.escape("line1\r\nline2"))
                .isEqualTo("\"line1\r\nline2\"");
        }

        @Test
        @DisplayName("multi-byte UTF-8 (Chinese) is preserved without quoting")
        void unicode() {
            assertThat(EncounterCsvWriter.escape("座头鲸")).isEqualTo("座头鲸");
        }
    }

    @Nested
    @DisplayName("header")
    class Header {

        @Test
        @DisplayName("emits all columns in stable order followed by CRLF")
        void headerLayout() throws Exception {
            StringWriter sw = new StringWriter();
            EncounterCsvWriter.header(sw);

            String out = sw.toString();
            assertThat(out).endsWith("\r\n");
            // Stable, documented column order
            assertThat(out).startsWith(
                "id,projectId,species,taxonomyId,status,encounterDate,location,locationId,"
                + "decimalLatitude,decimalLongitude,lifeStage,behavior,livingStatus,notes,"
                + "submitterUserId,assignedToUserId,individualId,observerId,occurrenceId\r\n");
        }
    }

    @Nested
    @DisplayName("row")
    class Row {

        @Test
        @DisplayName("simple row, no special chars, no nulls")
        void plainRow() throws Exception {
            Encounter e = baseEncounter();
            StringWriter sw = new StringWriter();
            EncounterCsvWriter.row(sw, e);

            String[] cells = sw.toString().replace("\r\n", "").split(",");
            assertThat(cells[0]).isEqualTo("42");                       // id
            assertThat(cells[1]).isEqualTo("1");                        // projectId
            assertThat(cells[2]).isEqualTo("Humpback whale");           // species
            assertThat(cells[3]).isEqualTo("7");                        // taxonomyId
            assertThat(cells[4]).isEqualTo("DRAFT");                    // status
            assertThat(cells[5]).isEqualTo("2026-06-11T08:30:00");      // encounterDate
            assertThat(cells[6]).isEqualTo("Maui");                     // location
            assertThat(cells[12]).isEqualTo("ALIVE");                   // livingStatus
            assertThat(cells[16]).isEqualTo("11");                      // individualId
            assertThat(cells[17]).isEqualTo("3");                       // observerId
            assertThat(sw.toString()).endsWith("\r\n");
        }

        @Test
        @DisplayName("nulls write as empty fields, not the literal 'null'")
        void nullsAreEmpty() throws Exception {
            Encounter e = new Encounter();
            e.setId(1L);              // only id set
            // Override the entity's field-init defaults so the test
            // actually exercises null fields. Encounter declares
            // status=DRAFT and livingStatus=UNKNOWN inline — we want
            // to see how writeField handles ACTUAL nulls here.
            e.setStatus(null);
            e.setLivingStatus(null);

            StringWriter sw = new StringWriter();
            EncounterCsvWriter.row(sw, e);

            String row = sw.toString().replace("\r\n", "");
            // 19 columns → 18 commas. First field = "1", rest empty.
            assertThat(row).isEqualTo("1,,,,,,,,,,,,,,,,,,");
            assertThat(row).doesNotContain("null");
        }

        @Test
        @DisplayName("comma in location → field is quoted in output")
        void commaInLocation() throws Exception {
            Encounter e = baseEncounter();
            e.setLocation("Monterey Bay, CA");
            StringWriter sw = new StringWriter();
            EncounterCsvWriter.row(sw, e);
            // Column index 6 = location. Splitting on commas isn't safe
            // when the field itself is quoted+comma; assert via substring.
            assertThat(sw.toString()).contains(",\"Monterey Bay, CA\",");
        }

        @Test
        @DisplayName("notes containing both a quote and a comma escape correctly")
        void notesWithQuoteAndComma() throws Exception {
            Encounter e = baseEncounter();
            e.setNotes("calf and mother, captain said \"distinctive\"");
            StringWriter sw = new StringWriter();
            EncounterCsvWriter.row(sw, e);
            assertThat(sw.toString()).contains(
                ",\"calf and mother, captain said \"\"distinctive\"\"\",");
        }

        @Test
        @DisplayName("absent relations (no individual/observer/taxonomy/occurrence) → empty id cells")
        void absentRelations() throws Exception {
            Encounter e = baseEncounter();
            e.setTaxonomy(null);
            e.setIndividual(null);
            e.setObserver(null);
            // (occurrence already null in baseEncounter)
            StringWriter sw = new StringWriter();
            EncounterCsvWriter.row(sw, e);

            String row = sw.toString().replace("\r\n", "");
            String[] cells = row.split(",", -1);  // -1 keeps trailing empties
            assertThat(cells[3]).isEmpty();   // taxonomyId
            assertThat(cells[16]).isEmpty();  // individualId
            assertThat(cells[17]).isEmpty();  // observerId
            assertThat(cells[18]).isEmpty();  // occurrenceId
        }
    }

    @Nested
    @DisplayName("header + multi-row stream")
    class FullDocument {

        @Test
        @DisplayName("writes header then two rows, all CRLF-terminated")
        void twoRows() throws Exception {
            StringWriter sw = new StringWriter();
            EncounterCsvWriter.header(sw);

            Encounter a = baseEncounter();
            EncounterCsvWriter.row(sw, a);

            Encounter b = baseEncounter();
            b.setId(43L);
            b.setSpecies("Orca");
            b.setStatus(EncounterStatus.PUBLISHED);
            EncounterCsvWriter.row(sw, b);

            String out = sw.toString();
            // Exactly 3 CRLFs (header + 2 rows)
            assertThat(out.split("\r\n", -1)).hasSize(4);  // trailing empty after final \r\n
            assertThat(out).contains(",Humpback whale,");
            assertThat(out).contains(",Orca,");
            assertThat(out).contains(",PUBLISHED,");
        }
    }

    // ---------- helpers ----------

    private Encounter baseEncounter() {
        Encounter e = new Encounter();
        e.setId(42L);
        e.setProjectId(1L);
        e.setSpecies("Humpback whale");
        e.setStatus(EncounterStatus.DRAFT);
        e.setEncounterDate(LocalDateTime.of(2026, 6, 11, 8, 30));
        e.setLocation("Maui");
        e.setLocationId("USA/HI/Maui");
        e.setDecimalLatitude(20.9);
        e.setDecimalLongitude(-156.5);
        e.setLifeStage("adult");
        e.setBehavior("feeding");
        e.setLivingStatus(LivingStatus.ALIVE);
        e.setNotes("calf and mother");
        e.setSubmitterUserId(99L);

        Taxonomy t = new Taxonomy();
        t.setId(7L);
        t.setScientificName("Megaptera novaeangliae");
        e.setTaxonomy(t);

        Individual ind = new Individual();
        ind.setId(11L);
        ind.setNickname("Salt");
        e.setIndividual(ind);

        Observer obs = new Observer();
        obs.setId(3L);
        obs.setName("Jane Doe");
        e.setObserver(obs);
        return e;
    }
}
