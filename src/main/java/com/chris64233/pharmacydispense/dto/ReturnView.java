package com.chris64233.pharmacydispense.dto;

import com.chris64233.pharmacydispense.domain.ReturnItem;
import com.chris64233.pharmacydispense.domain.ReturnRecord;

import java.time.Instant;
import java.util.List;

public record ReturnView(
        Long id,
        String bizNo,
        Long dispenseId,
        long quantity,
        Instant createdAt,
        List<Item> items) {

    public record Item(Long batchId, String batchNo, long quantity) {
        static Item from(ReturnItem i) {
            return new Item(i.getBatchId(), i.getBatchNo(), i.getQuantity());
        }
    }

    public static ReturnView from(ReturnRecord r) {
        return new ReturnView(r.getId(), r.getBizNo(), r.getDispenseId(),
                r.getQuantity(), r.getCreatedAt(),
                r.getItems().stream().map(Item::from).toList());
    }
}
