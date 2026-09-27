package com.chris64233.pharmacydispense.domain;

public enum BatchStatus {
    /** 正常，可调剂 */
    NORMAL,
    /** 冻结，暂停调剂 */
    FROZEN,
    /** 召回，禁止调剂 */
    RECALLED
}
