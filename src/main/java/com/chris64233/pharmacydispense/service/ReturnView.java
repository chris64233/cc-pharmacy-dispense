package com.chris64233.pharmacydispense.service;

import java.time.LocalDateTime;
import java.util.List;

public record ReturnView(String businessNo,
                         String dispenseBusinessNo,
                         long quantity,
                         LocalDateTime createdAt,
                         List<DispenseLineView> lines) {

    /** 一次原调剂的退药链：原调剂总量、累计已退、可退余额及全部退药记录。 */
    public record Chain(String dispenseBusinessNo,
                        long dispensedQuantity,
                        long returnedQuantity,
                        long returnableQuantity,
                        List<ReturnView> returns) {
    }
}
