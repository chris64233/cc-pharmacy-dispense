package com.chris64233.pharmacydispense.domain;

/**
 * 库存台账变动类型。
 */
public enum MovementType {
    /** 入库 */
    INBOUND,
    /** 调剂出库（按批次逐行记账） */
    DISPENSE,
    /** 退药回库（按批次逐行记账，回补到原批次） */
    RETURN
}
