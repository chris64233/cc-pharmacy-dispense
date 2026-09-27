package com.chris64233.pharmacydispense.repository;

import com.chris64233.pharmacydispense.domain.Prescription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /** 调剂/作废/退药前加写锁，串行化同一处方的并发操作。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prescription p where p.id = :id")
    Optional<Prescription> findByIdForUpdate(@Param("id") Long id);
}
