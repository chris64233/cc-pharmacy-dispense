package com.chris64233.pharmacydispense.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.pharmacydispense.domain.InventoryBatch;
import com.chris64233.pharmacydispense.domain.InventoryBatchRepository;
import com.chris64233.pharmacydispense.domain.StockMovement;
import com.chris64233.pharmacydispense.domain.StockMovementRepository;

/**
 * 库存台账查询：批次当前快照 + 只增不改的出入库流水。
 */
@Service
public class StockQueryService {

    private final StockMovementRepository movements;
    private final InventoryBatchRepository batches;

    public StockQueryService(StockMovementRepository movements,
                             InventoryBatchRepository batches) {
        this.movements = movements;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public List<StockMovementView> ledger(String drugCode) {
        List<StockMovement> list = drugCode == null || drugCode.isBlank()
                ? movements.findAllByOrderByIdAsc()
                : movements.findByBatch_DrugCodeOrderByIdAsc(drugCode);
        return list.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovementView.BatchSnapshot> batches(String drugCode) {
        List<InventoryBatch> list = drugCode == null || drugCode.isBlank()
                ? batches.findAll()
                : batches.findByDrugCodeOrderByExpiryDateAscIdAsc(drugCode);
        return list.stream()
                .map(b -> new StockMovementView.BatchSnapshot(b.getId(), b.getDrugCode(),
                        b.getBatchNo(), b.getQuantity(), b.getExpiryDate(),
                        b.getStatus().name()))
                .toList();
    }

    private StockMovementView toView(StockMovement m) {
        InventoryBatch b = m.getBatch();
        return new StockMovementView(m.getId(), b.getDrugCode(), b.getBatchNo(),
                m.getType().name(), m.getChangeQuantity(), m.getRunningBalance(),
                m.getRefBusinessNo(), m.getCreatedAt());
    }
}
