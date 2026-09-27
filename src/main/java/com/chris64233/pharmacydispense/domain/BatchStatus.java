package com.chris64233.pharmacydispense.domain;

/**
 * 批次状态：
 * ACTIVE 合格可调剂；EXPIRED 失效、RECALLED 召回、FROZEN 冻结均不可用于调剂。
 */
public enum BatchStatus {
    ACTIVE,
    EXPIRED,
    RECALLED,
    FROZEN
}
