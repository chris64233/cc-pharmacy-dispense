package com.chris64233.pharmacydispense.domain;

public enum PrescriptionStatus {
    /** 可调剂 */
    ACTIVE,
    /** 已调剂完成（剂量或次数用尽） */
    COMPLETED,
    /** 已作废 */
    CANCELLED
}
