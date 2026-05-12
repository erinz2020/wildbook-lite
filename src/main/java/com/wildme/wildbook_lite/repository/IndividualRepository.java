package com.wildme.wildbook_lite.repository;

import com.wildme.wildbook_lite.entity.Individual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IndividualRepository extends JpaRepository<Individual, Long>, JpaSpecificationExecutor<Individual> {
}
