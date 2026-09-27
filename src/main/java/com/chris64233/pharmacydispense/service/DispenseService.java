package com.chris64233.pharmacydispense.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.chris64233.pharmacydispense.error.ConflictException;

/**
 * 调剂入口：封装事务边界，并把幂等键唯一约束冲突（两个并发请求使用同一业务号、
 * 但作用于不同处方因而未在处方锁内串行）转化为可识别的冲突。
 */
@Service
public class DispenseService {

    private final DispenseTxService tx;
    private final DispenseQueryService queries;

    public DispenseService(DispenseTxService tx, DispenseQueryService queries) {
        this.tx = tx;
        this.queries = queries;
    }

    public DispenseOutcome dispense(String businessNo, String prescriptionNo, long quantity) {
        try {
            return tx.dispense(businessNo, prescriptionNo, quantity);
        } catch (DataIntegrityViolationException e) {
            var existing = queries.findDispenseByBusinessNo(businessNo);
            if (existing != null
                    && existing.getPrescription().getPrescriptionNo().equals(prescriptionNo)
                    && existing.getQuantity() == quantity) {
                // 并发的同内容请求：另一事务已提交完整调剂，按幂等返回
                return new DispenseOutcome(existing, true);
            }
            throw new ConflictException("业务号 " + businessNo + " 已存在且请求内容不一致", e);
        }
    }
}
