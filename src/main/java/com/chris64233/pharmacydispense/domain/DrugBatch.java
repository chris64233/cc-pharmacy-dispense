package com.chris64233.pharmacydispense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * 药品批次库存：按批次记录数量与有效期；冻结/召回批次不可调剂。
 */
@Entity
@Table(name = "drug_batches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"drugCode", "batchNo"}))
public class DrugBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String drugCode;

    @Column(nullable = false)
    private String batchNo;

    /** 入库数量（台账用） */
    @Column(nullable = false)
    private long initialQuantity;

    /** 当前剩余数量 */
    @Column(nullable = false)
    private long quantity;

    /** 有效期至（含当日） */
    @Column(nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.NORMAL;

    protected DrugBatch() {
    }

    public DrugBatch(String drugCode, String batchNo, long quantity, LocalDate expiryDate) {
        this.drugCode = drugCode;
        this.batchNo = batchNo;
        this.initialQuantity = quantity;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.status = BatchStatus.NORMAL;
    }

    public Long getId() {
        return id;
    }

    public String getDrugCode() {
        return drugCode;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public long getInitialQuantity() {
        return initialQuantity;
    }

    public long getQuantity() {
        return quantity;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public BatchStatus getStatus() {
        return status;
    }

    /** 以下方法仅供领域服务在持有行锁的事务内调用。 */

    public void deduct(long amount) {
        if (amount > this.quantity) {
            throw new IllegalStateException("批次库存不足，无法扣减");
        }
        this.quantity -= amount;
    }

    public void restore(long amount) {
        this.quantity += amount;
    }

    public void changeStatus(BatchStatus newStatus) {
        this.status = newStatus;
    }
}
