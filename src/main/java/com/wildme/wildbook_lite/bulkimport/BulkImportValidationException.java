package com.wildme.wildbook_lite.bulkimport;

import java.util.List;

import com.wildme.wildbook_lite.bulkimport.dto.ValidationError;

/**
 * Thrown when too many rows fail validation (row failure rate exceeds the
 * threshold) so the whole batch is rejected. Carries the per-row errors so
 * the handler can return them in the 422 response body. No task is created.
 */
public class BulkImportValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public BulkImportValidationException(List<ValidationError> errors) {
        super("Bulk import rejected: too many invalid rows (" + errors.size() + ")");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return errors;
    }
}
