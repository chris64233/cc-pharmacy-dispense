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
 * 退药批次行：记录本次退药恢复到哪个原批次、恢复多少。创建后不可修改。
 */
@Entity
@Table(name = "return_line")
public class ReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private ReturnRecord returnRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch batch;

    @Column(nullable = false)
    private long quantity;

    protected ReturnLine() {
    }

    ReturnLine(ReturnRecord returnRecord, InventoryBatch batch, long quantity) {
        this.returnRecord = returnRecord;
        this.batch = batch;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public ReturnRecord getReturnRecord() {
        return returnRecord;
    }

    public InventoryBatch getBatch() {
        return batch;
    }

    public long getQuantity() {
        return quantity;
    }
}
