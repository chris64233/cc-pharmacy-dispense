package com.chris64233.pharmacydispense.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StockMovementView(Long id,
                                String drugCode,
                                String batchNo,
                                String type,
                                long changeQuantity,
                                long runningBalance,
                                String refBusinessNo,
                                LocalDateTime createdAt) {

    /** 批次当前快照。 */
    public record BatchSnapshot(Long id,
                                String drugCode,
                                String batchNo,
                                long quantity,
                                LocalDate expiryDate,
                                String status) {
    }
}
