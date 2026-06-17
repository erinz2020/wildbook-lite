package com.wildme.wildbook_lite.export;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.wildme.wildbook_lite.entity.Encounter;

/**
 * Pure CSV serialization for {@link Encounter}.
 *
 * Why hand-rolled (no opencsv / apache-commons-csv):
 *  - The format is small (one fixed schema) and the rules are simple
 *    enough that the test suite can exhaustively cover them.
 *  - A library dependency for ~50 LOC is mass for no benefit, and a
 *    poorly-chosen lib (opencsv historical CVE-list, anyone?) is a
 *    standing risk.
 *  - Hand-rolling lets us inline the spec citations so future readers
 *    see exactly which RFC clause each quoting decision implements.
 *
 * RFC 4180 in one paragraph:
 *  - Lines separated by CRLF.
 *  - Each record is a sequence of fields separated by commas.
 *  - A field MAY be enclosed in double quotes. A field CONTAINING
 *    a comma, double quote, CR, or LF MUST be enclosed in double quotes.
 *  - A double quote inside a quoted field is escaped by doubling it.
 *
 * Choices we make beyond the bare minimum:
 *  - We always emit CRLF (`\r\n`) line endings.
 *  - We DO NOT BOM-prefix the file. Excel-on-Windows insists on BOM to
 *    detect UTF-8 — if/when that matters, write 0xEF 0xBB 0xBF at the
 *    very top before the header. Skipped here to keep the output a
 *    clean ASCII-subset CSV for jq / awk / pandas.
 *  - Nulls become empty fields, NOT the literal word "null".
 *  - LocalDateTime → ISO_LOCAL_DATE_TIME (e.g., "2026-06-11T08:30:00").
 *  - We expose relation IDs (individual_id, observer_id, ...) — NOT
 *    nicknames. Including nicknames would force a per-row lazy load
 *    of Individual/Observer (N+1). Callers wanting nicknames can join
 *    on the id columns client-side; keeping the export query a
 *    single-table scan is the right default.
 *
 * Encoding: ALWAYS UTF-8. Callers should configure their Writer with
 * StandardCharsets.UTF_8.
 */
public final class EncounterCsvWriter {

    private EncounterCsvWriter() {}  // static-only

    /** Stable column order. Document this in the SKILL.md export reference. */
    static final String[] COLUMNS = {
        "id",
        "projectId",
        "species",
        "taxonomyId",
        "status",
        "encounterDate",
        "location",
        "locationId",
        "decimalLatitude",
        "decimalLongitude",
        "lifeStage",
        "behavior",
        "livingStatus",
        "notes",
        "submitterUserId",
        "assignedToUserId",
        "individualId",
        "observerId",
        "occurrenceId"
    };

    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Writes the header row + CRLF. Must be called BEFORE any row(). */
    public static void header(Writer out) throws IOException {
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i > 0) out.write(',');
            // Headers themselves never contain special chars; written raw
            // would also be fine, but going through escape() keeps the
            // path uniform and resilient to future column-name changes.
            out.write(escape(COLUMNS[i]));
        }
        out.write("\r\n");
    }

    /** Writes one CSV row + CRLF. Column order matches {@link #COLUMNS}. */
    public static void row(Writer out, Encounter e) throws IOException {
        writeField(out, e.getId());                                            // id
        out.write(','); writeField(out, e.getProjectId());                     // projectId
        out.write(','); writeField(out, e.getSpecies());                       // species (denormalized)
        out.write(','); writeField(out, e.getTaxonomy() == null ? null : e.getTaxonomy().getId()); // taxonomyId
        out.write(','); writeField(out, e.getStatus() == null ? null : e.getStatus().name());      // status
        out.write(','); writeField(out, formatDt(e.getEncounterDate()));       // encounterDate
        out.write(','); writeField(out, e.getLocation());                      // location
        out.write(','); writeField(out, e.getLocationId());                    // locationId
        out.write(','); writeField(out, e.getDecimalLatitude());               // lat
        out.write(','); writeField(out, e.getDecimalLongitude());              // lng
        out.write(','); writeField(out, e.getLifeStage());                     // lifeStage
        out.write(','); writeField(out, e.getBehavior());                      // behavior
        out.write(','); writeField(out, e.getLivingStatus() == null ? null : e.getLivingStatus().name()); // livingStatus
        out.write(','); writeField(out, e.getNotes());                         // notes
        out.write(','); writeField(out, e.getSubmitterUserId());               // submitterUserId
        out.write(','); writeField(out, e.getAssignedToUserId());              // assignedToUserId
        out.write(','); writeField(out, e.getIndividual() == null ? null : e.getIndividual().getId());   // individualId
        out.write(','); writeField(out, e.getObserver()   == null ? null : e.getObserver().getId());     // observerId
        out.write(','); writeField(out, e.getOccurrence() == null ? null : e.getOccurrence().getId());   // occurrenceId
        out.write("\r\n");
    }

    // ----- helpers -----

    private static void writeField(Writer out, Object value) throws IOException {
        if (value == null) return;        // empty field
        out.write(escape(value.toString()));
    }

    private static String formatDt(LocalDateTime dt) {
        return dt == null ? null : DTF.format(dt);
    }

    /**
     * RFC 4180 quoting rules.
     *
     * Returns the original string when no special character is present
     * (cheap fast path), or a quoted-and-escaped form otherwise.
     */
    static String escape(String s) {
        if (s == null) return "";
        boolean needsQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || c == '"' || c == '\r' || c == '\n') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) return s;

        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append('"');  // double the embedded quote
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }
}
