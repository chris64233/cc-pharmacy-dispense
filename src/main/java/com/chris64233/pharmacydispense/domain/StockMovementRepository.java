package com.chris64233.pharmacydispense.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByBatch_IdOrderByIdAsc(Long batchId);

    List<StockMovement> findByBatch_DrugCodeOrderByIdAsc(String drugCode);

    List<StockMovement> findAllByOrderByIdAsc();
}
