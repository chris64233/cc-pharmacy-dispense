package com.chris64233.pharmacydispense.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnRecordRepository extends JpaRepository<ReturnRecord, Long> {

    boolean existsByBusinessNo(String businessNo);

    Optional<ReturnRecord> findByBusinessNo(String businessNo);

    List<ReturnRecord> findByOriginalDispense_IdOrderByIdAsc(Long dispenseId);

    List<ReturnRecord> findByOriginalDispense_Prescription_IdOrderByIdAsc(Long prescriptionId);

    /**
     * 原调剂累计已退总量。
     */
    @Query("select coalesce(sum(r.quantity), 0) from ReturnRecord r "
            + "where r.originalDispense.id = :dispenseId")
    long sumReturnedQuantity(@Param("dispenseId") Long dispenseId);

    /**
     * 原调剂某批次行的累计已退量，用于限制退药不超过各行原扣减量。
     */
    @Query("select coalesce(sum(l.quantity), 0) from ReturnLine l "
            + "where l.returnRecord.originalDispense.id = :dispenseId and l.batch.id = :batchId")
    long sumReturnedQuantityForBatch(@Param("dispenseId") Long dispenseId,
                                     @Param("batchId") Long batchId);
}
