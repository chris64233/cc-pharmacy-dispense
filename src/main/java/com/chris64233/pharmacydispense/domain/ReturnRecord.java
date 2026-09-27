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
 * 退药记录：针对一张已完成调剂的退药抬头，bizNo 为幂等业务号。
 * 记录创建后不可修改、不可删除。
 */
@Entity
@Table(name = "return_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bizNo"}))
public class ReturnRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 幂等业务号 */
    @Column(nullable = false)
    private String bizNo;

    /** 原调剂记录 */
    @Column(nullable = false)
    private Long dispenseId;

    /** 本次退药总量 */
    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReturnItem> items = new ArrayList<>();

    protected ReturnRecord() {
    }

    public ReturnRecord(String bizNo, Long dispenseId, long quantity, Instant createdAt) {
        this.bizNo = bizNo;
        this.dispenseId = dispenseId;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public void addItem(ReturnItem item) {
        items.add(item);
    }

    @PreUpdate
    @PreRemove
    void toImmutable() {
        throw new IllegalStateException("退药记录不可修改或删除");
    }

    public Long getId() {
        return id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public Long getDispenseId() {
        return dispenseId;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ReturnItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
