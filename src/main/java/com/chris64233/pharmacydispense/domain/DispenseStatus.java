package com.chris64233.pharmacydispense.domain;

/**
 * 调剂状态：COMPLETED 为唯一终态；任何校验失败都整体回滚，不会留下中间状态。
 */
public enum DispenseStatus {
    COMPLETED
}
