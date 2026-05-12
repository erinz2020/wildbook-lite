package com.wildme.wildbook_lite.repository;

import com.wildme.wildbook_lite.entity.Observer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ObserverRepository extends JpaRepository<Observer, Long>, JpaSpecificationExecutor<Observer> {
}
