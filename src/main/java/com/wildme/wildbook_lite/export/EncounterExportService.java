package com.wildme.wildbook_lite.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;

/**
 * Streams a project's encounters out as CSV.
 *
 * Memory contract: ONE Encounter row in memory at a time. The JPA
 * stream uses a server-side cursor (fetchSize=200, see
 * {@link com.wildme.wildbook_lite.repository.EncounterRepository#streamByProjectId})
 * and we write each row to the response output stream before the next
 * is fetched. A 10M-row export keeps the heap flat.
 *
 * Threading note: this runs on the StreamingResponseBody worker thread
 * (Spring spawns it from the request dispatcher). The @Transactional
 * proxy starts a fresh tx on whichever thread invokes the method — JPA
 * doesn't care which thread, only that all stream consumption happens
 * inside the same tx. The try-with-resources around the Stream
 * guarantees the cursor is closed even on IO failures mid-write.
 *
 * Security: ProjectGuard.canRead is checked HERE rather than in the
 * controller, because Spring Security propagates the SecurityContext
 * to the async dispatch thread automatically (since Spring Security
 * 5.x). Keeping the check in the service makes it canonical: anyone
 * instantiating this service directly can't skip it.
 *
 * IO model: we wrap the response OutputStream in a BufferedWriter so
 * the per-character writes inside EncounterCsvWriter don't translate
 * into syscalls. Buffer size = 16 KiB ≈ 80 rows-worth at typical CSV
 * widths — picked so a slow consumer doesn't stall the producer for
 * too long.
 */
@Service
public class EncounterExportService {

    /** Bigger than the default 8KB BufferedWriter so we batch more rows per write(). */
    private static final int BUFFER_SIZE = 16 * 1024;

    /** Flush periodically so a slow downstream still sees progress and isn't waiting on a full buffer. */
    private static final int FLUSH_EVERY = 500;

    private final EncounterRepository encRepo;
    private final ProjectGuard projectGuard;

    public EncounterExportService(EncounterRepository encRepo, ProjectGuard projectGuard) {
        this.encRepo = encRepo;
        this.projectGuard = projectGuard;
    }

    /**
     * Write the matching rows of {@code projectId} as CSV into {@code out}.
     * Does NOT close {@code out} — the caller (controller / StreamingResponseBody)
     * owns the response stream lifecycle.
     *
     * @throws ForbiddenException  caller lacks read access to the project
     * @throws UncheckedIOException any IO failure writing to {@code out} (matches
     *                              the contract of streams in the JDK — checked
     *                              IOException would bleed up through a Consumer)
     */
    @Audited("encounter.export.csv")
    @Transactional(readOnly = true)
    public long writeCsv(Long projectId, ExportFilters filters, OutputStream out) {
        if (!projectGuard.canRead(projectId)) {
            throw new ForbiddenException("No read access to project: " + projectId);
        }
        ExportFilters f = (filters == null) ? ExportFilters.NONE : filters;
        long written = 0L;

        // Important: do NOT close the underlying OutputStream — only flush
        // the BufferedWriter. The servlet container owns the response
        // stream and closes it itself.
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), BUFFER_SIZE);
        try {
            EncounterCsvWriter.header(w);

            // try-with-resources on the JPA stream guarantees the cursor
            // is closed. Streaming MUST stay inside this method so the
            // tx is still open while the cursor advances.
            try (Stream<Encounter> stream = encRepo.streamByProjectId(projectId)) {
                long localWritten = 0L;
                for (var it = stream.iterator(); it.hasNext(); ) {
                    Encounter e = it.next();
                    if (!f.accepts(e)) continue;
                    EncounterCsvWriter.row(w, e);
                    localWritten++;
                    if ((localWritten % FLUSH_EVERY) == 0) {
                        w.flush();
                    }
                }
                written = localWritten;
            }
            w.flush();
        } catch (IOException ioe) {
            // Wrap so we don't force every caller (StreamingResponseBody)
            // to declare/handle checked IOException through Spring's
            // ResponseEntity machinery.
            throw new UncheckedIOException("Failed writing CSV export", ioe);
        }
        return written;
    }
}
