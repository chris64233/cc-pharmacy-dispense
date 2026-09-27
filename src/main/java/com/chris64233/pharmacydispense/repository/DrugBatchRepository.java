package com.chris64233.pharmacydispense.repository;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.domain.DrugBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DrugBatchRepository extends JpaRepository<DrugBatch, Long> {

    /**
     * 查询某药品可用于调剂的批次（正常状态、未过期、有库存）并加写锁。
     * 所有批次锁统一按 id 升序获取（退药路径同样如此），避免不同事务
     * 以不同顺序加锁导致死锁；FEFO 的到期日排序在内存中完成。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from DrugBatch b where b.drugCode = :drugCode and b.status = :status "
            + "and b.expiryDate >= :today and b.quantity > 0 order by b.id asc")
    List<DrugBatch> findUsableForUpdate(@Param("drugCode") String drugCode,
                                        @Param("status") BatchStatus status,
                                        @Param("today") LocalDate today);

    /** 退药回补时按 id 升序加写锁，避免与调剂锁顺序不一致造成死锁。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from DrugBatch b where b.id in :ids order by b.id asc")
    List<DrugBatch> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    List<DrugBatch> findByDrugCodeOrderByExpiryDateAscIdAsc(String drugCode);
}
