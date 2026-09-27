package com.chris64233.pharmacydispense.service;

import com.chris64233.pharmacydispense.domain.DispenseRecord;

/**
 * 调剂结果：replayed=true 表示命中幂等重放（返回既有完整调剂），false 表示新完成的调剂。
 */
public record DispenseOutcome(DispenseRecord record, boolean replayed) {
}
