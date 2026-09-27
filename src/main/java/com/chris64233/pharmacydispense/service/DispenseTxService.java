package com.chris64233.pharmacydispense.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.domain.DispenseRecord;
import com.chris64233.pharmacydispense.domain.DispenseRecordRepository;
import com.chris64233.pharmacydispense.domain.InventoryBatch;
import com.chris64233.pharmacydispense.domain.InventoryBatchRepository;
import com.chris64233.pharmacydispense.domain.MovementType;
import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionRepository;
import com.chris64233.pharmacydispense.domain.StockMovement;
import com.chris64233.pharmacydispense.domain.StockMovementRepository;
import com.chris64233.pharmacydispense.error.BusinessRuleException;
import com.chris64233.pharmacydispense.error.ConflictException;
import com.chris64233.pharmacydispense.error.NotFoundException;

/**
 * 调剂事务核心：一个方法 = 一个数据库事务，任何校验失败都整体回滚。
 *
 * 加锁顺序固定为：处方行 → 该药品全部合格批次行（按批次 id 升序）。
 */
@Service
public class DispenseTxService {

    private final PrescriptionRepository prescriptions;
    private final InventoryBatchRepository batches;
    private final DispenseRecordRepository dispenses;
    private final StockMovementRepository movements;
    private final Clock clock;

    public DispenseTxService(PrescriptionRepository prescriptions,
                             InventoryBatchRepository batches,
                             DispenseRecordRepository dispenses,
                             StockMovementRepository movements, Clock clock) {
        this.prescriptions = prescriptions;
        this.batches = batches;
        this.dispenses = dispenses;
        this.movements = movements;
        this.clock = clock;
    }

    @Transactional
    public DispenseOutcome dispense(String businessNo, String prescriptionNo, long quantity) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 处方行悲观写锁：同一处方的并发调剂在此串行
        Prescription prescription = prescriptions.findForUpdateByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new NotFoundException("处方不存在: " + prescriptionNo));

        // 2. 幂等：锁内复查业务号，重复请求直接返回原调剂
        DispenseRecord existing = dispenses.findByBusinessNo(businessNo).orElse(null);
        if (existing != null) {
            verifyReplay(existing, prescription, quantity);
            return new DispenseOutcome(existing, true);
        }

        // 3. 处方与数量校验
        if (prescription.isVoid()) {
            throw new BusinessRuleException("处方已作废，不能调剂");
        }
        if (today.isBefore(prescription.getValidFrom()) || today.isAfter(prescription.getValidUntil())) {
            throw new BusinessRuleException("处方不在有效期内");
        }
        if (quantity <= 0) {
            throw new BusinessRuleException("调剂量必须为正");
        }
        if (quantity > prescription.getPerDoseLimit()) {
            throw new BusinessRuleException("单次调剂量 " + quantity
                    + " 超过单次上限 " + prescription.getPerDoseLimit());
        }
        if (quantity > prescription.remainingQuantity()) {
            throw new BusinessRuleException("累计调剂量将超过处方剩余量：剩余 "
                    + prescription.remainingQuantity() + "，本次申请 " + quantity);
        }
        if (!prescription.canUseCount()) {
            throw new BusinessRuleException("已达允许调剂次数 "
                    + prescription.getAllowedDispenseCount());
        }

        // 4. 锁定该药品全部 ACTIVE 批次（SQL 按 id 升序取锁），再做 FEFO 分配
        List<InventoryBatch> lockedBatches =
                batches.findActiveForUpdate(prescription.getDrugCode(), BatchStatus.ACTIVE);
        List<BatchAllocator.AllocationLine> plan =
                BatchAllocator.allocateFefo(lockedBatches, today, quantity);

        // 5. 执行扣减、写调剂行与台账；任一步失败由事务整体回滚，不产生部分扣减
        DispenseRecord record = new DispenseRecord(businessNo, prescription, quantity, now);
        for (BatchAllocator.AllocationLine line : plan) {
            InventoryBatch batch = line.batch();
            batch.deduct(line.quantity());
            record.addLine(batch, line.quantity());
            movements.save(new StockMovement(batch, MovementType.DISPENSE, -line.quantity(),
                    batch.getQuantity(), businessNo, now));
        }

        // 6. 推进处方累计量与次数（同一行锁保护，不可能超量/超次数）
        prescription.registerDispense(quantity);
        return new DispenseOutcome(dispenses.save(record), false);
    }

    private void verifyReplay(DispenseRecord existing, Prescription prescription, long quantity) {
        if (!existing.getPrescription().getId().equals(prescription.getId())
                || existing.getQuantity() != quantity) {
            throw new ConflictException("业务号 " + existing.getBusinessNo()
                    + " 已用于其他调剂，请求内容不一致");
        }
    }
}
