package com.chris64233.pharmacydispense;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.ZoneOffset;

/** 固定“今天”为 2026-09-27（UTC），用于批次过期与处方有效期规则测试。 */
@TestConfiguration
public class FixedClockConfig {

    public static final Clock FIXED =
            Clock.fixed(java.time.Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC);

    @Bean
    @Primary
    public Clock fixedClock() {
        return FIXED;
    }
}
