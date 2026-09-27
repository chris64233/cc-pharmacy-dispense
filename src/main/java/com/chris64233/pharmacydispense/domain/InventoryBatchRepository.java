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

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    /**
     * 锁定某药品当前所有 ACTIVE 批次，统一按 id 升序加锁（与退药回库的加锁顺序一致，避免死锁）。
     * 取锁后在 Java 中按到期日做 FEFO 分配；状态变更事务因需要行写锁会与本查询互斥，
     * 因此锁内看到的 ACTIVE 状态在提交前保持有效。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.drugCode = :drug and b.status = :status order by b.id asc")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    List<InventoryBatch> findActiveForUpdate(@Param("drug") String drugCode,
                                             @Param("status") BatchStatus status);

    /**
     * 退药回库时按 id 升序锁定原调剂涉及的批次（不限状态：被冻结/召回的批次也要恢复库存）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.id in :ids order by b.id asc")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    List<InventoryBatch> findByIdsForUpdate(@Param("ids") List<Long> ids);

    Optional<InventoryBatch> findByDrugCodeAndBatchNo(String drugCode, String batchNo);

    List<InventoryBatch> findByDrugCodeOrderByExpiryDateAscIdAsc(String drugCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.id = :id")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    Optional<InventoryBatch> findForUpdateById(@Param("id") Long id);
}
