package com.chris64233.pharmacydispense.domain;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByPrescriptionNo(String prescriptionNo);

    boolean existsByPrescriptionNo(String prescriptionNo);

    /**
     * 处方行悲观写锁：调剂、退药、作废都先取此锁，
     * 保证同一处方上的累计量、次数、状态变更全局串行。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prescription p where p.prescriptionNo = :no")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    Optional<Prescription> findForUpdateByPrescriptionNo(@Param("no") String prescriptionNo);
}
