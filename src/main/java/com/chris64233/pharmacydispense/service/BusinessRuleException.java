package com.chris64233.pharmacydispense.service;

/** 业务规则校验失败（含库存不足）：整个事务回滚，不产生任何部分扣减。 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
