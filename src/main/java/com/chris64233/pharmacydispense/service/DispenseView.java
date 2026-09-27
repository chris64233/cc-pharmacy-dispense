package com.chris64233.pharmacydispense.service;

import java.time.LocalDateTime;
import java.util.List;

public record DispenseView(String businessNo,
                           String prescriptionNo,
                           long quantity,
                           String status,
                           LocalDateTime createdAt,
                           List<DispenseLineView> lines) {
}
