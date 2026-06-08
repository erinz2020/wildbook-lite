package com.wildme.wildbook_lite.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.dto.ImportResult;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.project.ProjectGuard;
import com.wildme.wildbook_lite.repository.EncounterRepository;

/**
 * Bulk import from CSV. "Best-effort" semantics: each row gets its own
 * sub-transaction, so a bad row does not roll back successful rows.
 *
 * Why per-row REQUIRES_NEW (and not one big transaction):
 *
 *  - One-big-transaction = ALL-OR-NOTHING. If row 50,000 fails, you redo
 *    the first 49,999. Bad UX for large files.
 *  - Per-row REQUIRES_NEW = report exactly which rows failed and why,
 *    user fixes the bad ones and re-uploads only those.
 *  - Alternative for very strict use cases (financial): full-batch tx
 *    with explicit savepoints, but that's not what this domain needs.
 *
 * The wrapper method is NOT itself @Transactional — that would create an
 * outer tx that swallows the inner REQUIRES_NEW commits if it rolls back.
 */
@Service
public class EncounterImportService {

    /** Self-injection so per-row REQUIRES_NEW calls actually go through the proxy. */
    private final EncounterImportService self;
    private final EncounterRepository encRepo;
    private final ProjectGuard projectGuard;

    public EncounterImportService(@org.springframework.context.annotation.Lazy EncounterImportService self,
                                  EncounterRepository encRepo,
                                  ProjectGuard projectGuard) {
        this.self = self;
        this.encRepo = encRepo;
        this.projectGuard = projectGuard;
    }

    @Audited("encounter.import")
    public ImportResult importCsv(Long projectId, MultipartFile file) {
        if (!projectGuard.canWrite(projectId)) {
            throw new ForbiddenException("No write access to project: " + projectId);
        }
        if (file.isEmpty()) {
            throw new BusinessException("Upload is empty");
        }

        int total = 0, ok = 0, failed = 0;
        List<ImportResult.RowError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // skip
            if (header == null) {
                throw new BusinessException("CSV is missing header row");
            }
            // Expected header (case-insensitive contains check)
            if (!header.toLowerCase().contains("species") || !header.toLowerCase().contains("location")) {
                throw new BusinessException("CSV header must include 'species' and 'location'");
            }

            String line;
            int rowNum = 1; // header was row 1
            while ((line = reader.readLine()) != null) {
                rowNum++;
                total++;
                try {
                    self.importOneRow(projectId, line, rowNum);
                    ok++;
                } catch (Exception e) {
                    failed++;
                    errors.add(new ImportResult.RowError(rowNum, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new BusinessException("Failed to read upload: " + e.getMessage());
        }

        return new ImportResult(total, ok, failed, errors);
    }

    /**
     * Each row's own tx. Must be public + invoked through `self` so AOP proxy
     * actually wraps it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importOneRow(Long projectId, String line, int rowNum) {
        String[] parts = splitCsv(line);
        if (parts.length < 2) {
            throw new IllegalArgumentException("expected at least 2 columns (species,location)");
        }
        String species  = parts[0].trim();
        String location = parts[1].trim();
        String notes    = parts.length > 2 ? parts[2].trim() : null;

        if (species.isEmpty())  throw new IllegalArgumentException("species is required");
        if (location.isEmpty()) throw new IllegalArgumentException("location is required");

        Encounter enc = new Encounter();
        enc.setProjectId(projectId);
        enc.setSpecies(species);
        enc.setLocation(location);
        enc.setNotes(notes);
        encRepo.save(enc);
    }

    /** Minimal CSV splitter: respects double-quoted fields with internal commas. */
    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); i++; // escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
