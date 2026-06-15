package com.wildme.wildbook_lite.repository;

import java.util.Optional;

import com.wildme.wildbook_lite.entity.Observer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ObserverRepository extends JpaRepository<Observer, Long>, JpaSpecificationExecutor<Observer> {

    /** Case-insensitive lookup by name — drives the bulk-import find-or-create flow. */
    Optional<Observer> findFirstByNameIgnoreCase(String name);
}
