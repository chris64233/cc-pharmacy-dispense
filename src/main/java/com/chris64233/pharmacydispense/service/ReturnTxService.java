package com.chris64233.pharmacydispense.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.pharmacydispense.domain.DispenseLine;
import com.chris64233.pharmacydispense.domain.DispenseRecord;
import com.chris64233.pharmacydispense.domain.DispenseRecordRepository;
import com.chris64233.pharmacydispense.domain.InventoryBatch;
import com.chris64233.pharmacydispense.domain.InventoryBatchRepository;
import com.chris64233.pharmacydispense.domain.MovementType;
import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionRepository;
import com.chris64233.pharmacydispense.domain.ReturnRecord;
import com.chris64233.pharmacydispense.domain.ReturnRecordRepository;
import com.chris64233.pharmacydispense.domain.StockMovement;
import com.chris64233.pharmacydispense.domain.StockMovementRepository;
import com.chris64233.pharmacydispense.error.BusinessRuleException;
import com.chris64233.pharmacydispense.error.ConflictException;
import com.chris64233.pharmacydispense.error.NotFoundException;

/**
 * 退药事务核心：只能针对已完成调剂退药，按原调剂批次行恢复库存、恢复处方余额。
 *
 * 加锁顺序固定为：处方行 → 原调剂行 → 原批次行（按批次 id 升序）。
 */
@Service
public class ReturnTxService {

    private final PrescriptionRepository prescriptions;
    private final DispenseRecordRepository dispenses;
    private final ReturnRecordRepository returns;
    private final InventoryBatchRepository batches;
    private final StockMovementRepository movements;
    private final Clock clock;

    public ReturnTxService(PrescriptionRepository prescriptions,
                           DispenseRecordRepository dispenses,
                           ReturnRecordRepository returns,
                           InventoryBatchRepository batches,
                           StockMovementRepository movements, Clock clock) {
        this.prescriptions = prescriptions;
        this.dispenses = dispenses;
        this.returns = returns;
        this.batches = batches;
        this.movements = movements;
        this.clock = clock;
    }

    @Transactional
    public ReturnOutcome returnMedicine(String businessNo, String dispenseBusinessNo,
                                        long quantity) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (quantity <= 0) {
            throw new BusinessRuleException("退药量必须为正");
        }

        // 1. 仅查原调剂所属处方号（标量查询，不加载任何实体，避免加锁后版本冲突）
        String prescriptionNo = dispenses.findPrescriptionNoByBusinessNo(dispenseBusinessNo)
                .orElseThrow(() -> new NotFoundException(
                        "原调剂不存在: " + dispenseBusinessNo));

        // 2. 处方行悲观写锁：与所有调剂/作废/退药事务互斥，
        //    且必须先于调剂行/批次锁获取，与调剂事务保持相同加锁顺序以防死锁
        Prescription prescription = prescriptions
                .findForUpdateByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new IllegalStateException("原调剂所属处方缺失，数据不一致"));

        // 3. 锁原调剂行：进一步串行化针对同一调剂的并发退药
        DispenseRecord dispense = dispenses.findForUpdateByBusinessNo(dispenseBusinessNo)
                .orElseThrow();

        // 4. 幂等复查
        ReturnRecord existing = returns.findByBusinessNo(businessNo).orElse(null);
        if (existing != null) {
            verifyReplay(existing, dispense, quantity);
            return new ReturnOutcome(existing, true);
        }

        long alreadyReturned = returns.sumReturnedQuantity(dispense.getId());
        long returnable = dispense.getQuantity() - alreadyReturned;
        if (quantity > returnable) {
            throw new BusinessRuleException("退药量 " + quantity
                    + " 超过原调剂可退量 " + returnable
                    + "（原调剂量 " + dispense.getQuantity() + "，已退 " + alreadyReturned + "）");
        }

        // 5. 原批次行按 FEFO 顺序（原调剂行顺序）计算各行可退量
        List<DispenseLine> originalLines = dispense.getLines();
        List<Long> batchIds = originalLines.stream()
                .map(line -> line.getBatch().getId())
                .distinct()
                .toList();
        // 按 id 升序取锁，避免与调剂事务形成等待环
        Map<Long, InventoryBatch> batchById = new LinkedHashMap<>();
        for (InventoryBatch b : batches.findByIdsForUpdate(batchIds)) {
            batchById.put(b.getId(), b);
        }

        List<BatchAllocator.AllocationLine> plan = new ArrayList<>();
        long remaining = quantity;
        for (DispenseLine line : originalLines) {
            if (remaining == 0) {
                break;
            }
            long lineReturned =
                    returns.sumReturnedQuantityForBatch(dispense.getId(), line.getBatch().getId());
            long lineReturnable = line.getQuantity() - lineReturned;
            long take = Math.min(lineReturnable, remaining);
            if (take > 0) {
                InventoryBatch batch = batchById.get(line.getBatch().getId());
                if (batch == null) {
                    throw new IllegalStateException("原批次记录缺失，数据不一致");
                }
                plan.add(new BatchAllocator.AllocationLine(batch, take));
                remaining -= take;
            }
        }
        if (remaining > 0) {
            // 与总量校验理论上一致到达不了，保留以防数据异常
            throw new BusinessRuleException("退药分配失败，剩余 " + remaining);
        }

        // 6. 恢复批次库存、写退药行与回库台账（批次即使已冻结/召回也恢复，只是不再参与新调剂）
        ReturnRecord record = new ReturnRecord(businessNo, dispense, quantity, now);
        for (BatchAllocator.AllocationLine line : plan) {
            InventoryBatch batch = line.batch();
            batch.restock(line.quantity());
            record.addLine(batch, line.quantity());
            movements.save(new StockMovement(batch, MovementType.RETURN, line.quantity(),
                    batch.getQuantity(), businessNo, now));
        }

        // 7. 恢复处方余额；次数不恢复
        prescription.restoreOnReturn(quantity);
        return new ReturnOutcome(returns.save(record), false);
    }

    private void verifyReplay(ReturnRecord existing, DispenseRecord dispense, long quantity) {
        if (!existing.getOriginalDispense().getId().equals(dispense.getId())
                || existing.getQuantity() != quantity) {
            throw new ConflictException("业务号 " + existing.getBusinessNo()
                    + " 已用于其他退药，请求内容不一致");
        }
    }
}
