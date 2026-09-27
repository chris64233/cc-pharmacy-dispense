package com.chris64233.pharmacydispense.service;

/**
 * 事务内发现同一 bizNo 已被并发事务落库：外观层据此重放原记录，
 * 而不是把失败暴露给调用方（幂等语义）。
 */
class ConcurrentBizNoException extends RuntimeException {
    ConcurrentBizNoException(String message) {
        super(message);
    }
}
