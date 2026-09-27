package com.chris64233.pharmacydispense.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.chris64233.pharmacydispense.error.ConflictException;

/**
 * 退药入口：封装事务边界，处理退药业务号唯一约束的并发冲突。
 */
@Service
public class ReturnService {

    private final ReturnTxService tx;
    private final ReturnQueryService queries;

    public ReturnService(ReturnTxService tx, ReturnQueryService queries) {
        this.tx = tx;
        this.queries = queries;
    }

    public ReturnOutcome returnMedicine(String businessNo, String dispenseBusinessNo,
                                        long quantity) {
        try {
            return tx.returnMedicine(businessNo, dispenseBusinessNo, quantity);
        } catch (DataIntegrityViolationException e) {
            var existing = queries.findReturnByBusinessNo(businessNo);
            if (existing != null
                    && existing.getOriginalDispense().getBusinessNo()
                            .equals(dispenseBusinessNo)
                    && existing.getQuantity() == quantity) {
                return new ReturnOutcome(existing, true);
            }
            throw new ConflictException("退药业务号 " + businessNo + " 已存在且请求内容不一致", e);
        }
    }
}
