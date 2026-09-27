package com.chris64233.pharmacydispense.service;

import com.chris64233.pharmacydispense.domain.ReturnRecord;

/**
 * 退药结果：replayed=true 表示命中幂等重放。
 */
public record ReturnOutcome(ReturnRecord record, boolean replayed) {
}
