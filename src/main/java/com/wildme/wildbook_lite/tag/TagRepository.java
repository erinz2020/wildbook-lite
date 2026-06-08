package com.wildme.wildbook_lite.tag;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByProjectId(Long projectId);

    Optional<Tag> findByProjectIdAndName(Long projectId, String name);

    boolean existsByProjectIdAndName(Long projectId, String name);
}
