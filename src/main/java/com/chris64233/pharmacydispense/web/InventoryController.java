package com.chris64233.pharmacydispense.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.pharmacydispense.domain.InventoryBatch;
import com.chris64233.pharmacydispense.service.InventoryService;
import com.chris64233.pharmacydispense.service.StockMovementView;
import com.chris64233.pharmacydispense.service.StockQueryService;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventory;
    private final StockQueryService stockQueries;

    public InventoryController(InventoryService inventory, StockQueryService stockQueries) {
        this.inventory = inventory;
        this.stockQueries = stockQueries;
    }

    /** 批次入库。 */
    @PostMapping("/inbound")
    public ResponseEntity<StockMovementView.BatchSnapshot> inbound(
            @Valid @RequestBody InboundRequest req) {
        InventoryBatch batch = inventory.inbound(req.drugCode(), req.batchNo(),
                req.quantity(), req.expiryDate(), req.inboundNo());
        return ResponseEntity.created(URI.create("/api/inventory/batches/" + batch.getId()))
                .body(new StockMovementView.BatchSnapshot(batch.getId(), batch.getDrugCode(),
                        batch.getBatchNo(), batch.getQuantity(), batch.getExpiryDate(),
                        batch.getStatus().name()));
    }

    /** 批次状态变更（EXPIRED / RECALLED / FROZEN / ACTIVE）。 */
    @PutMapping("/batches/{id}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id,
                                             @Valid @RequestBody ChangeBatchStatusRequest req) {
        inventory.changeBatchStatus(id, req.status());
        return ResponseEntity.noContent().build();
    }

    /** 批次当前库存快照。 */
    @GetMapping("/batches")
    public List<StockMovementView.BatchSnapshot> batches(
            @RequestParam(required = false) String drugCode) {
        return stockQueries.batches(drugCode);
    }

    /** 库存台账流水（可按药品过滤）。 */
    @GetMapping("/ledger")
    public List<StockMovementView> ledger(@RequestParam(required = false) String drugCode) {
        return stockQueries.ledger(drugCode);
    }
}
