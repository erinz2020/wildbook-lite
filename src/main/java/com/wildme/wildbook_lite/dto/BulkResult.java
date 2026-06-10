package com.wildme.wildbook_lite.dto;

import java.util.List;

/**
 * Generic best-effort batch response. Mirrors ImportResult but keyed by
 * the entity id instead of the file row number.
 */
public record BulkResult(
    int total,
    int succeeded,
    int failed,
    List<ItemError> errors
) {
    public record ItemError(Long id, String reason) {}
}
