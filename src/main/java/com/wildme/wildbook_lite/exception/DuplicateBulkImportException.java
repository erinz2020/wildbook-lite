package com.wildme.wildbook_lite.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a bulk import is submitted with a bulkImportId that already
 * exists. The client used a duplicate idempotency key → 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateBulkImportException extends BusinessException {
    public DuplicateBulkImportException(String message) {
        super(message);
    }
}
