package com.chris64233.pharmacydispense;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 固定业务时钟：所有测试以 2026-09-27 为“今天”，批次到期判断结果确定。
 * 测试上下文同时存在系统时钟和固定时钟，固定时钟标记 @Primary 优先注入。
 */
@TestConfiguration
public class FixedClockConfig {

    public static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
