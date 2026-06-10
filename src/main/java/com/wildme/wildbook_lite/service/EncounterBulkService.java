package com.wildme.wildbook_lite.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.dto.BulkResult;
import com.wildme.wildbook_lite.encounter.EncounterStatus;

/**
 * Best-effort batch operations on encounters.
 *
 * Same shape as EncounterImportService:
 *
 *   public method (the orchestrator) iterates the inputs and for each
 *   calls `self.doOne(...)` which is @Transactional(REQUIRES_NEW).
 *   Each item gets its own sub-transaction; a bad item rolls back only
 *   itself, the rest stay committed. Errors are collected and returned
 *   as a structured report.
 *
 * Why @Lazy self-injection (interview gold):
 *
 *   Spring AOP is *proxy-based*. When you call `this.doOne(...)` from
 *   within the same bean, you hit the raw object, not the proxy — so
 *   the @Transactional advice never fires. Going through the proxy
 *   means routing the call through the IoC container.
 *
 *   The fix: inject a reference to the bean itself (`self`) so the
 *   call goes through the proxy. @Lazy breaks the circular reference
 *   ("I depend on myself") that would otherwise crash bean creation.
 *
 *   This is one of the most asked Spring gotchas in interviews.
 */
@Service
public class EncounterBulkService {

    private final EncounterBulkService self;
    private final EncounterService encounterService;

    public EncounterBulkService(@Lazy EncounterBulkService self,
                                EncounterService encounterService) {
        this.self = self;
        this.encounterService = encounterService;
    }

    @Audited("encounter.bulk-transition")
    public BulkResult bulkTransition(List<Long> ids, EncounterStatus toStatus) {
        int ok = 0, failed = 0;
        List<BulkResult.ItemError> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                self.transitionOne(id, toStatus);
                ok++;
            } catch (Exception e) {
                failed++;
                errors.add(new BulkResult.ItemError(id, e.getMessage()));
            }
        }
        return new BulkResult(ids.size(), ok, failed, errors);
    }

    @Audited("encounter.bulk-delete")
    public BulkResult bulkDelete(List<Long> ids) {
        int ok = 0, failed = 0;
        List<BulkResult.ItemError> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                self.deleteOne(id);
                ok++;
            } catch (Exception e) {
                failed++;
                errors.add(new BulkResult.ItemError(id, e.getMessage()));
            }
        }
        return new BulkResult(ids.size(), ok, failed, errors);
    }

    /**
     * MUST be public and called through `self.` for the REQUIRES_NEW
     * proxy advice to apply.
     *
     * We delegate to EncounterService.transition() which already
     * handles validation, role checks, history, events. The
     * REQUIRES_NEW here just isolates each item.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionOne(Long id, EncounterStatus toStatus) {
        encounterService.transition(id, toStatus);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteOne(Long id) {
        encounterService.deleteById(id);
    }
}
