package com.chris64233.pharmacydispense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** 统一时钟，便于测试中控制“今天”以验证批次过期与处方有效期规则。 */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
