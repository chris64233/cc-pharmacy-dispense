package com.chris64233.pharmacydispense.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * 一次完整调剂记录。创建后不可修改；退药只新增退药记录，不回写本记录。
 */
@Entity
@Table(name = "dispense_record")
public class DispenseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 调剂业务号，幂等键，全局唯一 */
    @Column(nullable = false, unique = true, length = 64)
    private String businessNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    /** 本次调剂总量（= 各批次行数量之和） */
    @Column(nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DispenseStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "dispense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DispenseLine> lines = new ArrayList<>();

    protected DispenseRecord() {
    }

    public DispenseRecord(String businessNo, Prescription prescription, long quantity,
                          LocalDateTime createdAt) {
        this.businessNo = businessNo;
        this.prescription = prescription;
        this.quantity = quantity;
        this.status = DispenseStatus.COMPLETED;
        this.createdAt = createdAt;
    }

    public void addLine(InventoryBatch batch, long lineQuantity) {
        this.lines.add(new DispenseLine(this, batch, lineQuantity));
    }

    public List<DispenseLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Long getId() {
        return id;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public Prescription getPrescription() {
        return prescription;
    }

    public long getQuantity() {
        return quantity;
    }

    public DispenseStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
