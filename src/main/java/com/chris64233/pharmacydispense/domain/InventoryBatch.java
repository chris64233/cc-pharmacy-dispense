package com.chris64233.pharmacydispense.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * 药品库存批次：按批次记录库存数量和有效期，并带质量状态。
 * 只有 ACTIVE 且在调剂当日仍未到期的批次可用于调剂。
 */
@Entity
@Table(name = "inventory_batch", uniqueConstraints =
        @UniqueConstraint(name = "uk_batch_drug_no", columnNames = {"drugCode", "batchNo"}))
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String drugCode;

    @Column(nullable = false, length = 64)
    private String batchNo;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Version
    private long version;

    protected InventoryBatch() {
    }

    public InventoryBatch(String drugCode, String batchNo, long quantity,
                          LocalDate expiryDate, LocalDateTime createdAt) {
        this.drugCode = drugCode;
        this.batchNo = batchNo;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.status = BatchStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public void deduct(long amount) {
        if (amount <= 0) {
            throw new IllegalStateException("扣减量必须为正");
        }
        if (amount > quantity) {
            // 正常流程在扣减前已做总量校验与行锁，走到这里说明并发数据异常
            throw new IllegalStateException("批次库存不足");
        }
        this.quantity -= amount;
    }

    /**
     * 退药回库：回补到原批次（即使批次后来被冻结/召回，库存仍恢复，只是不再参与后续调剂）。
     */
    public void restock(long amount) {
        if (amount <= 0) {
            throw new IllegalStateException("回库量必须为正");
        }
        this.quantity += amount;
    }

    public void changeStatus(BatchStatus newStatus) {
        this.status = newStatus;
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

    public long getQuantity() {
        return quantity;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
