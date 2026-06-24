package com.wildme.wildbook_lite.bulkimport;

import java.util.List;

/**
 * The raw import data, persisted as JSON in BulkImportTask.payloadJson so the
 * async runner can pick the task up later (survives a restart). Holds exactly
 * what the runner needs to turn rows into records: the column order
 * (fieldNames) and the data (rows).
 */
public record BulkImportPayload(
    List<String> fieldNames,
    List<List<String>> rows
) {
}
