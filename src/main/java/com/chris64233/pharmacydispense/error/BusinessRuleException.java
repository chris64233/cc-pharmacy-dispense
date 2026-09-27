package com.chris64233.pharmacydispense.error;

/**
 * 业务规则校验失败：处方失效、超余额、超单次上限、超次数、批次不合格/不足、退药超量等。
 * 服务端不接受该请求（422），事务整体回滚，不产生任何部分扣减。
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
