package com.chris64233.pharmacydispense.dto;

import com.chris64233.pharmacydispense.domain.DispenseItem;
import com.chris64233.pharmacydispense.domain.DispenseRecord;

import java.time.Instant;
import java.util.List;

public record DispenseView(
        Long id,
        String bizNo,
        Long prescriptionId,
        String drugCode,
        long quantity,
        Instant createdAt,
        List<Item> items) {

    public record Item(Long batchId, String batchNo, long quantity) {
        static Item from(DispenseItem i) {
            return new Item(i.getBatchId(), i.getBatchNo(), i.getQuantity());
        }
    }

    public static DispenseView from(DispenseRecord r) {
        return new DispenseView(r.getId(), r.getBizNo(), r.getPrescriptionId(), r.getDrugCode(),
                r.getQuantity(), r.getCreatedAt(),
                r.getItems().stream().map(Item::from).toList());
    }
}
