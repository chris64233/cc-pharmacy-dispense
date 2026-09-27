package com.chris64233.pharmacydispense;

import org.springframework.jdbc.core.JdbcTemplate;

/** 测试间清理：直接 SQL 清表（绕过实体的不可变回调，仅用于测试隔离）。 */
public final class TestDbCleaner {

    private TestDbCleaner() {
    }

    public static void cleanAll(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM return_items");
        jdbc.update("DELETE FROM return_records");
        jdbc.update("DELETE FROM dispense_items");
        jdbc.update("DELETE FROM dispense_records");
        jdbc.update("DELETE FROM drug_batches");
        jdbc.update("DELETE FROM prescriptions");
    }
}
