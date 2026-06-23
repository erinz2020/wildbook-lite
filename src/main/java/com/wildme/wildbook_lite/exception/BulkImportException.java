package com.wildme.wildbook_lite.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
/**
 * General bulk import failure (table-level validation errors: missing
 * required column, duplicate column names, etc.) → 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BulkImportException extends BusinessException {
    public BulkImportException(String message) {
        super(message);
    }
}