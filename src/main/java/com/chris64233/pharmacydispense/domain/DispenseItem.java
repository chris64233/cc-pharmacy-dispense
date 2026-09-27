package com.chris64233.pharmacydispense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 调剂明细：一次调剂从某个批次扣减的数量。不可修改。
 */
@Entity
@Table(name = "dispense_items")
public class DispenseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private DispenseRecord record;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private String batchNo;

    @Column(nullable = false)
    private long quantity;

    protected DispenseItem() {
    }

    public DispenseItem(DispenseRecord record, Long batchId, String batchNo, long quantity) {
        this.record = record;
        this.batchId = batchId;
        this.batchNo = batchNo;
        this.quantity = quantity;
    }

    @PreUpdate
    @PreRemove
    void toImmutable() {
        throw new IllegalStateException("调剂明细不可修改或删除");
    }

    public Long getId() {
        return id;
    }

    public DispenseRecord getRecord() {
        return record;
    }

    public Long getBatchId() {
        return batchId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public long getQuantity() {
        return quantity;
    }
}
