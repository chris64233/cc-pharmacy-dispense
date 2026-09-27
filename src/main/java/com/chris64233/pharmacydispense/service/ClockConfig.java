package com.chris64233.pharmacydispense.service;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一时钟：业务日期（批次到期判断）与时间戳均取自此 Bean，测试可用固定时钟覆盖。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
