package com.chris64233.pharmacydispense.repository;

import com.chris64233.pharmacydispense.domain.DispenseRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DispenseRecordRepository extends JpaRepository<DispenseRecord, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<DispenseRecord> findByBizNo(String bizNo);

    /** 退药前锁定原调剂记录，串行化针对同一调剂的并发退药。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DispenseRecord r where r.id = :id")
    Optional<DispenseRecord> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "items")
    List<DispenseRecord> findByPrescriptionIdOrderByIdAsc(Long prescriptionId);
}
