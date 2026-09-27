package com.chris64233.pharmacydispense.dto;

import com.chris64233.pharmacydispense.domain.BatchStatus;
import com.chris64233.pharmacydispense.domain.DrugBatch;

import java.time.LocalDate;

public record BatchView(
        Long id,
        String drugCode,
        String batchNo,
        long initialQuantity,
        long quantity,
        LocalDate expiryDate,
        BatchStatus status) {

    public static BatchView from(DrugBatch b) {
        return new BatchView(b.getId(), b.getDrugCode(), b.getBatchNo(),
                b.getInitialQuantity(), b.getQuantity(), b.getExpiryDate(), b.getStatus());
    }
}
