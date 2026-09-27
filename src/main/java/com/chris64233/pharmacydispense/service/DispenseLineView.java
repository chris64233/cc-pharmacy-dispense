package com.chris64233.pharmacydispense.service;

import java.time.LocalDateTime;

/**
 * 调剂批次行视图。
 */
public record DispenseLineView(String batchNo, long quantity) {
}
