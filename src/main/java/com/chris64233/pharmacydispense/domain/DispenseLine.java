package com.chris64233.pharmacydispense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 调剂批次行：记录本次调剂从哪个批次扣减了多少，用于退药时按原批次恢复库存。
 * 创建后不可修改。
 */
@Entity
@Table(name = "dispense_line")
public class DispenseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispense_id", nullable = false)
    private DispenseRecord dispense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch batch;

    @Column(nullable = false)
    private long quantity;

    protected DispenseLine() {
    }

    DispenseLine(DispenseRecord dispense, InventoryBatch batch, long quantity) {
        this.dispense = dispense;
        this.batch = batch;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public DispenseRecord getDispense() {
        return dispense;
    }

    public InventoryBatch getBatch() {
        return batch;
    }

    public long getQuantity() {
        return quantity;
    }
}
