package com.wildme.wildbook_lite.bulkimport;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkImportTaskRepository extends JpaRepository<BulkImportTask, Long> {
    Optional<BulkImportTask> findByBulkImportId(UUID bulkImportId);
}
