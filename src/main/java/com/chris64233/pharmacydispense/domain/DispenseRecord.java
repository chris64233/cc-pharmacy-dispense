package com.chris64233.pharmacydispense.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 调剂记录：一次调剂的抬头，bizNo 为幂等业务号。
 * 记录创建后不可修改、不可删除。
 */
@Entity
@Table(name = "dispense_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bizNo"}))
public class DispenseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 幂等业务号 */
    @Column(nullable = false)
    private String bizNo;

    @Column(nullable = false)
    private Long prescriptionId;

    @Column(nullable = false)
    private String drugCode;

    /** 本次调剂总量 */
    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DispenseItem> items = new ArrayList<>();

    protected DispenseRecord() {
    }

    public DispenseRecord(String bizNo, Long prescriptionId, String drugCode, long quantity, Instant createdAt) {
        this.bizNo = bizNo;
        this.prescriptionId = prescriptionId;
        this.drugCode = drugCode;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public void addItem(DispenseItem item) {
        items.add(item);
    }

    @PreUpdate
    @PreRemove
    void toImmutable() {
        throw new IllegalStateException("调剂记录不可修改或删除");
    }

    public Long getId() {
        return id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public Long getPrescriptionId() {
        return prescriptionId;
    }

    public String getDrugCode() {
        return drugCode;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<DispenseItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
