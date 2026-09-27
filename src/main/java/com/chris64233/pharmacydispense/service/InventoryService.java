package com.chris64233.pharmacydispense.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.domain.InventoryBatch;
import com.chris64233.pharmacydispense.domain.InventoryBatchRepository;
import com.chris64233.pharmacydispense.domain.MovementType;
import com.chris64233.pharmacydispense.domain.StockMovement;
import com.chris64233.pharmacydispense.domain.StockMovementRepository;
import com.chris64233.pharmacydispense.error.BusinessRuleException;
import com.chris64233.pharmacydispense.error.ConflictException;
import com.chris64233.pharmacydispense.error.NotFoundException;

@Service
public class InventoryService {

    private final InventoryBatchRepository batches;
    private final StockMovementRepository movements;
    private final Clock clock;

    public InventoryService(InventoryBatchRepository batches,
                            StockMovementRepository movements, Clock clock) {
        this.batches = batches;
        this.movements = movements;
        this.clock = clock;
    }

    /**
     * 入库：登记新批次并写入入库台账流水。
     */
    @Transactional
    public InventoryBatch inbound(String drugCode, String batchNo, long quantity,
                                  LocalDate expiryDate, String inboundNo) {
        if (quantity <= 0) {
            throw new BusinessRuleException("入库数量必须为正");
        }
        if (expiryDate == null) {
            throw new BusinessRuleException("批次有效期不能为空");
        }
        if (batches.findByDrugCodeAndBatchNo(drugCode, batchNo).isPresent()) {
            throw new BusinessRuleException(
                    "批次已存在: " + drugCode + "/" + batchNo);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        InventoryBatch batch = batches.save(new InventoryBatch(drugCode, batchNo, quantity,
                expiryDate, now));
        movements.save(new StockMovement(batch, MovementType.INBOUND, quantity, quantity,
                inboundNo, now));
        return batch;
    }

    /**
     * 变更批次质量状态（失效/召回/冻结/恢复合格）。行写锁与调剂事务互斥。
     */
    @Transactional
    public void changeBatchStatus(Long batchId, BatchStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessRuleException("目标状态不能为空");
        }
        try {
            InventoryBatch batch = batches.findForUpdateById(batchId)
                    .orElseThrow(() -> new NotFoundException("批次不存在: " + batchId));
            batch.changeStatus(newStatus);
        } catch (ConcurrencyFailureException e) {
            throw new ConflictException("并发状态变更冲突，请重试", e);
        }
    }

    @Transactional(readOnly = true)
    public InventoryBatch getBatch(Long id) {
        return batches.findById(id)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<InventoryBatch> listBatches(String drugCode) {
        return batches.findByDrugCodeOrderByExpiryDateAscIdAsc(drugCode);
    }
}
