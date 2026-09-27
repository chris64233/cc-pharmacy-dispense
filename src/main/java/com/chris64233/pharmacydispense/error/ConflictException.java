package com.chris64233.pharmacydispense.error;

/**
 * 冲突（409）：幂等键复用于不同请求内容，或并发冲突导致锁等待失败。
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
