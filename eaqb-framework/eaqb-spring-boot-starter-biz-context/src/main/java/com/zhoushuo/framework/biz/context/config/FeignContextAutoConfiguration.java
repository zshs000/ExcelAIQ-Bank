package com.zhoushuo.framework.biz.context.config;

import com.zhoushuo.framework.biz.context.interceptor.TrustedFeignRequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class FeignContextAutoConfiguration {

    @Bean
    public TrustedFeignRequestInterceptor trustedFeignRequestInterceptor(
            @Value("${spring.application.name:unknown-service}") String callerService,
            @Value("${internal.auth.secret:change-me-in-prod}") String internalAuthSecret) {
        return new TrustedFeignRequestInterceptor(callerService, internalAuthSecret);
    }
}
