package com.chris64233.pharmacydispense.domain;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface DispenseRecordRepository extends JpaRepository<DispenseRecord, Long> {

    Optional<DispenseRecord> findByBusinessNo(String businessNo);

    /**
     * 仅取原调剂所属处方号（标量查询，不把处方/调剂实体装入持久化上下文），
     * 供退药事务在加锁前定位处方，避免“先读到旧版本、行锁后读到新版本”的版本冲突。
     */
    @Query("select d.prescription.prescriptionNo from DispenseRecord d "
            + "where d.businessNo = :no")
    Optional<String> findPrescriptionNoByBusinessNo(@Param("no") String businessNo);

    boolean existsByBusinessNo(String businessNo);

    List<DispenseRecord> findByPrescription_IdOrderByIdAsc(Long prescriptionId);

    /**
     * 退药时锁定原调剂记录，串行化针对同一调剂的并发退药。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DispenseRecord d where d.businessNo = :no")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    Optional<DispenseRecord> findForUpdateByBusinessNo(@Param("no") String businessNo);
}
