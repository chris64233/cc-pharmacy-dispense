package com.chris64233.pharmacydispense.service;

import com.chris64233.pharmacydispense.domain.DispenseRecord;
import com.chris64233.pharmacydispense.domain.DrugBatch;
import com.chris64233.pharmacydispense.domain.Prescription;
import com.chris64233.pharmacydispense.domain.ReturnRecord;
import com.chris64233.pharmacydispense.dto.CreateBatchRequest;
import com.chris64233.pharmacydispense.dto.CreatePrescriptionRequest;
import com.chris64233.pharmacydispense.repository.DispenseRecordRepository;
import com.chris64233.pharmacydispense.repository.DrugBatchRepository;
import com.chris64233.pharmacydispense.repository.PrescriptionRepository;
import com.chris64233.pharmacydispense.repository.ReturnRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 处方调剂领域服务（无事务外观）。
 *
 * 职责：幂等预检、唯一约束冲突后的重放，以及只读查询。
 * 真正的加锁与扣减在 {@link PharmacyTxOperations} 的单个事务内完成，
 * 保证“全部成功或整体回滚”，不会留下部分扣减。
 */
@Service
public class PharmacyService {

    private final PharmacyTxOperations tx;
    private final PrescriptionRepository prescriptions;
    private final DrugBatchRepository batches;
    private final DispenseRecordRepository dispenses;
    private final ReturnRecordRepository returns;

    public PharmacyService(PharmacyTxOperations tx,
                           PrescriptionRepository prescriptions,
                           DrugBatchRepository batches,
                           DispenseRecordRepository dispenses,
                           ReturnRecordRepository returns) {
        this.tx = tx;
        this.prescriptions = prescriptions;
        this.batches = batches;
        this.dispenses = dispenses;
        this.returns = returns;
    }

    // ==================== 建档 / 库存维护 ====================

    public Prescription createPrescription(CreatePrescriptionRequest req) {
        return tx.createPrescription(req);
    }

    public DrugBatch createBatch(CreateBatchRequest req) {
        return tx.createBatch(req);
    }

    public DrugBatch changeBatchStatus(Long batchId, com.chris64233.pharmacydispense.domain.BatchStatus newStatus) {
        return tx.changeBatchStatus(batchId, newStatus);
    }

    // ==================== 调剂（幂等外观） ====================

    /**
     * 按处方分次调剂。同一 bizNo 的重复/并发调用只会有一笔调剂落库，
     * 其余调用重放返回原记录。
     */
    public DispenseRecord dispense(Long prescriptionId, String bizNo, long quantity) {
        DispenseRecord existing = dispenses.findByBizNo(bizNo).orElse(null);
        if (existing != null) {
            return replayOrReject(existing, prescriptionId, quantity);
        }
        try {
            return tx.dispense(prescriptionId, bizNo, quantity);
        } catch (ConcurrentBizNoException e) {
            // 持锁后发现同 bizNo 已被并发事务落库
            return replayOrReject(loadDispense(bizNo), prescriptionId, quantity);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 唯一约束兜底：败者不报错，重放胜者的结果，保证幂等
            return replayOrReject(loadDispense(bizNo), prescriptionId, quantity);
        }
    }

    // ==================== 退药（幂等外观） ====================

    public ReturnRecord returnDispense(Long dispenseId, String bizNo, long quantity) {
        ReturnRecord existing = returns.findByBizNo(bizNo).orElse(null);
        if (existing != null) {
            return replayReturnOrReject(existing, dispenseId, quantity);
        }
        try {
            return tx.returnDispense(dispenseId, bizNo, quantity);
        } catch (ConcurrentBizNoException e) {
            return replayReturnOrReject(loadReturn(bizNo), dispenseId, quantity);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return replayReturnOrReject(loadReturn(bizNo), dispenseId, quantity);
        }
    }

    // ==================== 作废 ====================

    public Prescription cancelPrescription(Long prescriptionId) {
        return tx.cancelPrescription(prescriptionId);
    }

    // ==================== 查询 ====================

    public Prescription getPrescription(Long id) {
        return prescriptions.findById(id)
                .orElseThrow(() -> new NotFoundException("处方不存在: " + id));
    }

    public List<DispenseRecord> listDispenses(Long prescriptionId) {
        if (!prescriptions.existsById(prescriptionId)) {
            throw new NotFoundException("处方不存在: " + prescriptionId);
        }
        return dispenses.findByPrescriptionIdOrderByIdAsc(prescriptionId);
    }

    public DispenseRecord getDispense(Long id) {
        return dispenses.findById(id)
                .orElseThrow(() -> new NotFoundException("调剂记录不存在: " + id));
    }

    public List<ReturnRecord> listReturns(Long dispenseId) {
        if (!dispenses.existsById(dispenseId)) {
            throw new NotFoundException("调剂记录不存在: " + dispenseId);
        }
        return returns.findByDispenseIdOrderByIdAsc(dispenseId);
    }

    /** 库存台账：批次入库量、当前量、状态与有效期。 */
    public List<DrugBatch> ledger(String drugCode) {
        if (drugCode == null || drugCode.isBlank()) {
            return batches.findAll();
        }
        return batches.findByDrugCodeOrderByExpiryDateAscIdAsc(drugCode);
    }

    // ==================== 内部 ====================

    private DispenseRecord loadDispense(String bizNo) {
        return dispenses.findByBizNo(bizNo)
                .orElseThrow(() -> new IdempotencyConflictException("业务号冲突且无法找到原记录: " + bizNo));
    }

    private ReturnRecord loadReturn(String bizNo) {
        return returns.findByBizNo(bizNo)
                .orElseThrow(() -> new IdempotencyConflictException("业务号冲突且无法找到原记录: " + bizNo));
    }

    private DispenseRecord replayOrReject(DispenseRecord existing, Long prescriptionId, long quantity) {
        if (!existing.getPrescriptionId().equals(prescriptionId) || existing.getQuantity() != quantity) {
            throw new IdempotencyConflictException(
                    "业务号 " + existing.getBizNo() + " 已存在，但处方或调剂量与本次请求不一致");
        }
        return existing;
    }

    private ReturnRecord replayReturnOrReject(ReturnRecord existing, Long dispenseId, long quantity) {
        if (!existing.getDispenseId().equals(dispenseId) || existing.getQuantity() != quantity) {
            throw new IdempotencyConflictException(
                    "业务号 " + existing.getBizNo() + " 已存在，但原调剂或退药量与本次请求不一致");
        }
        return existing;
    }
}
