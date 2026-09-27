package com.chris64233.pharmacydispense.service;

/** 幂等重试时业务号已存在但请求参数与原记录不一致。 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
