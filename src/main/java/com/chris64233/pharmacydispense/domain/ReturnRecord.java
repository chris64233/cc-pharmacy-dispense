package com.chris64233.pharmacydispense.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * 退药记录：针对一次已完成调剂发起，退药量不得超过原调剂量（扣除已退部分）。
 * 创建后不可修改。
 */
@Entity
@Table(name = "return_record")
public class ReturnRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 退药业务号，幂等键，全局唯一 */
    @Column(nullable = false, unique = true, length = 64)
    private String businessNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_dispense_id", nullable = false)
    private DispenseRecord originalDispense;

    /** 本次退药总量（= 各批次行数量之和），不得超过原调剂未退量 */
    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "returnRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ReturnLine> lines = new ArrayList<>();

    protected ReturnRecord() {
    }

    public ReturnRecord(String businessNo, DispenseRecord originalDispense, long quantity,
                        LocalDateTime createdAt) {
        this.businessNo = businessNo;
        this.originalDispense = originalDispense;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public void addLine(InventoryBatch batch, long lineQuantity) {
        this.lines.add(new ReturnLine(this, batch, lineQuantity));
    }

    public List<ReturnLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Long getId() {
        return id;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public DispenseRecord getOriginalDispense() {
        return originalDispense;
    }

    public long getQuantity() {
        return quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
