package com.wildme.wildbook_lite.bulkimport;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.wildme.wildbook_lite.bulkimport.dto.BulkImportResponse;

/**
 * MapStruct mapper: Entity -> DTO. You declare the contract; MapStruct
 * generates the implementation at compile time (no reflection).
 *
 * componentModel = "spring" makes the generated impl a Spring @Component,
 * so it can be constructor-injected like any other bean.
 */
@Mapper(componentModel = "spring")
public interface BulkImportMapper {

    // Fields with the same name map automatically (status, totalRows, ...).
    // Only the mismatch needs spelling out: response.taskId <- task.getId().
    @Mapping(target = "taskId", source = "id")
    BulkImportResponse toResponse(BulkImportTask task);
}
