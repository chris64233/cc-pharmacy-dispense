package com.chris64233.pharmacydispense.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 库存台账流水：入库、调剂出库、退药回库均逐批次记账，只增不改。
 * runningBalance 为该批次记账后的库存结余。
 */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MovementType type;

    /** 变动量：入库/退药为正，调剂为负 */
    @Column(nullable = false)
    private long changeQuantity;

    /** 记账后批次结余 */
    @Column(nullable = false)
    private long runningBalance;

    /** 关联业务号（调剂/退药业务号；入库为入库单号） */
    @Column(nullable = false, length = 64)
    private String refBusinessNo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected StockMovement() {
    }

    public StockMovement(InventoryBatch batch, MovementType type, long changeQuantity,
                         long runningBalance, String refBusinessNo, LocalDateTime createdAt) {
        this.batch = batch;
        this.type = type;
        this.changeQuantity = changeQuantity;
        this.runningBalance = runningBalance;
        this.refBusinessNo = refBusinessNo;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public InventoryBatch getBatch() {
        return batch;
    }

    public MovementType getType() {
        return type;
    }

    public long getChangeQuantity() {
        return changeQuantity;
    }

    public long getRunningBalance() {
        return runningBalance;
    }

    public String getRefBusinessNo() {
        return refBusinessNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
