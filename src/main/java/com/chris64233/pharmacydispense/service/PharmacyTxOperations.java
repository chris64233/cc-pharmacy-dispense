package com.chris64233.pharmacydispense.service;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.domain.DispenseItem;
import com.chris64233.pharmacydispense.domain.DispenseRecord;
import com.chris64233.pharmacydispense.domain.DrugBatch;
import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.PrescriptionStatus;
import com.chris64233.pharmacydispense.domain.ReturnItem;
import com.chris64233.pharmacydispense.domain.ReturnRecord;
import com.chris64233.pharmacydispense.dto.CreateBatchRequest;
import com.chris64233.pharmacydispense.dto.CreatePrescriptionRequest;
import com.chris64233.pharmacydispense.repository.DispenseRecordRepository;
import com.chris64233.pharmacydispense.repository.DrugBatchRepository;
import com.chris64233.pharmacydispense.repository.PrescriptionRepository;
import com.chris64233.pharmacydispense.repository.ReturnRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 所有写操作的事务边界。
 *
 * 加锁顺序（全系统统一，避免死锁）：
 * 1) 先锁处方行（PESSIMISTIC_WRITE）；
 * 2) 再按 id 升序锁相关批次行；退药时先锁原调剂记录。
 *
 * 由此保证：
 * - 同一处方的并发调剂串行执行，不会超量、超次数、库存为负；
 * - 作废与调剂互斥，结果只能是“作废”或“一次完整调剂”之一；
 * - 任何校验失败都抛出运行时异常，整事务回滚，不留部分扣减。
 */
@Service
public class PharmacyTxOperations {

    private final PrescriptionRepository prescriptions;
    private final DrugBatchRepository batches;
    private final DispenseRecordRepository dispenses;
    private final ReturnRecordRepository returns;
    private final Clock clock;

    public PharmacyTxOperations(PrescriptionRepository prescriptions,
                                DrugBatchRepository batches,
                                DispenseRecordRepository dispenses,
                                ReturnRecordRepository returns,
                                Clock clock) {
        this.prescriptions = prescriptions;
        this.batches = batches;
        this.dispenses = dispenses;
        this.returns = returns;
        this.clock = clock;
    }

    // ==================== 建档 / 库存维护 ====================

    @Transactional
    public Prescription createPrescription(CreatePrescriptionRequest req) {
        if (req.validTo().isBefore(req.validFrom())) {
            throw new BusinessRuleException("处方有效期止不得早于有效期起");
        }
        if (req.maxPerDispense() > req.totalQuantity()) {
            throw new BusinessRuleException("单次上限不得超过处方总剂量");
        }
        String drugName = req.drugName() == null || req.drugName().isBlank()
                ? req.drugCode() : req.drugName();
        return prescriptions.save(new Prescription(req.patientName(), req.drugCode(), drugName,
                req.totalQuantity(), req.maxPerDispense(),
                req.validFrom(), req.validTo(), req.allowedDispenseCount()));
    }

    @Transactional
    public DrugBatch createBatch(CreateBatchRequest req) {
        return batches.save(new DrugBatch(req.drugCode(), req.batchNo(), req.quantity(), req.expiryDate()));
    }

    @Transactional
    public DrugBatch changeBatchStatus(Long batchId, BatchStatus newStatus) {
        DrugBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchId));
        batch.changeStatus(newStatus);
        return batch;
    }

    // ==================== 调剂 ====================

    @Transactional
    public DispenseRecord dispense(Long prescriptionId, String bizNo, long quantity) {
        // 1. 锁定处方行，串行化同一处方上的所有并发写操作
        Prescription p = lockPrescription(prescriptionId);

        // 2. 持锁后再次确认幂等（与同 bizNo 的并发请求在锁后串行到达）
        DispenseRecord existing = dispenses.findByBizNo(bizNo).orElse(null);
        if (existing != null) {
            throw new ConcurrentBizNoException(bizNo);
        }

        LocalDate today = LocalDate.now(clock);
        if (p.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new BusinessRuleException("处方已作废，不能调剂");
        }
        if (p.getStatus() == PrescriptionStatus.COMPLETED) {
            throw new BusinessRuleException("处方已完成，剩余可调剂次数或剂量为 0");
        }
        if (today.isBefore(p.getValidFrom()) || today.isAfter(p.getValidTo())) {
            throw new BusinessRuleException("处方不在有效期内");
        }
        if (quantity > p.getMaxPerDispense()) {
            throw new BusinessRuleException(
                    "单次调剂量 " + quantity + " 超过单次上限 " + p.getMaxPerDispense());
        }
        if (quantity > p.remainingQuantity()) {
            throw new BusinessRuleException(
                    "累计调剂量将超过处方剩余量 " + p.remainingQuantity());
        }
        if (p.remainingCount() <= 0) {
            throw new BusinessRuleException("已达允许调剂次数 " + p.getAllowedDispenseCount());
        }

        // 3. 锁定合格批次（正常 + 未过期 + 有库存），行锁统一按 id 升序获取
        List<DrugBatch> usable = new ArrayList<>(batches.findUsableForUpdate(
                p.getDrugCode(), BatchStatus.NORMAL, today));
        // FEFO：锁内按到期日升序（同日按 id）决定扣减顺序
        usable.sort(Comparator.comparing(DrugBatch::getExpiryDate)
                .thenComparing(DrugBatch::getId));
        long available = usable.stream().mapToLong(DrugBatch::getQuantity).sum();
        if (available < quantity) {
            // 尚未做任何扣减；失败即整体回滚
            throw new BusinessRuleException(
                    "合格批次库存不足：需要 " + quantity + "，可用 " + available
                            + "（过期/召回/冻结批次不可用）");
        }

        // 4. FEFO 分配并逐批扣减（均在锁内，库存不可能为负）
        List<DispenseItem> items = new ArrayList<>();
        long need = quantity;
        for (DrugBatch batch : usable) {
            if (need == 0) {
                break;
            }
            long take = Math.min(need, batch.getQuantity());
            batch.deduct(take);
            items.add(new DispenseItem(null, batch.getId(), batch.getBatchNo(), take));
            need -= take;
        }
        if (need != 0) {
            throw new BusinessRuleException("批次库存扣减异常，事务整体回滚");
        }

        // 5. 落不可变调剂记录并回写处方累计量
        DispenseRecord record = new DispenseRecord(bizNo, p.getId(), p.getDrugCode(),
                quantity, Instant.now(clock));
        DispenseRecord saved = dispenses.save(record);
        for (DispenseItem item : items) {
            saved.addItem(new DispenseItem(saved, item.getBatchId(),
                    item.getBatchNo(), item.getQuantity()));
        }
        p.addDispense(quantity);
        return saved;
    }

    // ==================== 退药 ====================

    @Transactional
    public ReturnRecord returnDispense(Long dispenseId, String bizNo, long quantity) {
        // 锁定原调剂记录，串行化针对同一调剂的并发退药
        DispenseRecord dispense = dispenses.findByIdForUpdate(dispenseId)
                .orElseThrow(() -> new NotFoundException("调剂记录不存在: " + dispenseId));
        // 再锁处方，保持“处方先于批次”的统一加锁顺序
        Prescription p = lockPrescription(dispense.getPrescriptionId());

        if (returns.findByBizNo(bizNo).isPresent()) {
            throw new ConcurrentBizNoException(bizNo);
        }

        // 汇总该调剂已发生的退药：按批次统计可退余量
        List<ReturnRecord> chain = returns.findByDispenseIdOrderByIdAsc(dispenseId);
        Map<Long, Long> returnedByBatch = new HashMap<>();
        long returnedTotal = 0;
        for (ReturnRecord r : chain) {
            returnedTotal += r.getQuantity();
            for (ReturnItem item : r.getItems()) {
                returnedByBatch.merge(item.getBatchId(), item.getQuantity(), Long::sum);
            }
        }
        long returnable = dispense.getQuantity() - returnedTotal;
        if (returnable <= 0) {
            throw new BusinessRuleException("原调剂已全额退清，不能重复退药");
        }
        if (quantity > returnable) {
            throw new BusinessRuleException(
                    "退药量 " + quantity + " 超过原调剂剩余可退量 " + returnable);
        }

        // 按原调剂的批次构成回补，行锁按 id 升序；退药不能超过该批次原扣减量
        List<Long> batchIds = dispense.getItems().stream()
                .map(DispenseItem::getBatchId).distinct().sorted().toList();
        Map<Long, DrugBatch> batchMap = new HashMap<>();
        for (DrugBatch b : batches.findAllByIdForUpdate(batchIds)) {
            batchMap.put(b.getId(), b);
        }

        List<ReturnItem> newItems = new ArrayList<>();
        long need = quantity;
        for (DispenseItem taken : dispense.getItems()) {
            if (need == 0) {
                break;
            }
            long already = returnedByBatch.getOrDefault(taken.getBatchId(), 0L);
            long batchReturnable = taken.getQuantity() - already;
            long give = Math.min(need, batchReturnable);
            if (give <= 0) {
                continue;
            }
            DrugBatch batch = batchMap.get(taken.getBatchId());
            if (batch == null) {
                throw new NotFoundException("原调剂批次已不存在: " + taken.getBatchId());
            }
            batch.restore(give);
            newItems.add(new ReturnItem(null, batch.getId(), batch.getBatchNo(), give));
            need -= give;
        }
        if (need != 0) {
            throw new BusinessRuleException("退药回补异常，事务整体回滚");
        }

        ReturnRecord record = new ReturnRecord(bizNo, dispenseId, quantity, Instant.now(clock));
        ReturnRecord saved = returns.save(record);
        for (ReturnItem item : newItems) {
            saved.addItem(new ReturnItem(saved, item.getBatchId(),
                    item.getBatchNo(), item.getQuantity()));
        }
        // 剂量每次退药都回补；次数仅在该次调剂被全额退清时回补一次
        p.revertDispense(quantity, quantity == returnable);
        return saved;
    }

    // ==================== 作废 ====================

    /**
     * 作废处方。与调剂并发时，由处方行锁保证结果只能是“作废”或“一次完整调剂”。
     */
    @Transactional
    public Prescription cancelPrescription(Long prescriptionId) {
        Prescription p = lockPrescription(prescriptionId);
        if (p.getStatus() == PrescriptionStatus.CANCELLED) {
            return p; // 作废本身幂等
        }
        if (p.getDispensedQuantity() > 0) {
            throw new BusinessRuleException(
                    "处方已有调剂记录，不能作废；请先通过退药处理（已调剂 "
                            + p.getDispensedQuantity() + "）");
        }
        p.cancel();
        return p;
    }

    private Prescription lockPrescription(Long id) {
        return prescriptions.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("处方不存在: " + id));
    }
}
