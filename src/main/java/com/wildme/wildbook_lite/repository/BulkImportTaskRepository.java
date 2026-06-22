package com.wildme.wildbook_lite.repository;
import com.wildme.wildbook_lite.entity.BulkImportTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkImportTaskRepository extends JpaRepository<BulkImportTask, Long> {
    Optional<BulkImportTask> findByBulkImportId(String bulkImportId);
}