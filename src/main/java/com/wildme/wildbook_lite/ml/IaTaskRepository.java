package com.wildme.wildbook_lite.ml;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IaTaskRepository extends JpaRepository<IaTask, Long> {

    Page<IaTask> findByAnnotationIdOrderByCreatedAtDesc(Long annotationId, Pageable pageable);

    Page<IaTask> findBySubmitterUserIdOrderByCreatedAtDesc(Long submitterUserId, Pageable pageable);

    List<IaTask> findByStatusOrderByCreatedAtAsc(IaTaskStatus status);
}
