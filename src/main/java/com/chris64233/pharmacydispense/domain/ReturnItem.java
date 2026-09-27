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
 * 退药明细：本次退药回补到某个批次的数量。不可修改。
 */
@Entity
@Table(name = "return_items")
public class ReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private ReturnRecord record;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private String batchNo;

    @Column(nullable = false)
    private long quantity;

    protected ReturnItem() {
    }

    public ReturnItem(ReturnRecord record, Long batchId, String batchNo, long quantity) {
        this.record = record;
        this.batchId = batchId;
        this.batchNo = batchNo;
        this.quantity = quantity;
    }

    @PreUpdate
    @PreRemove
    void toImmutable() {
        throw new IllegalStateException("退药明细不可修改或删除");
    }

    public Long getId() {
        return id;
    }

    public ReturnRecord getRecord() {
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
