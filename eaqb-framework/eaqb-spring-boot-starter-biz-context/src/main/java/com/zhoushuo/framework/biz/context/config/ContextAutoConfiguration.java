package com.zhoushuo.framework.biz.context.config;

import com.zhoushuo.framework.biz.context.filter.TrustedInternalRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
public class ContextAutoConfiguration {

    @Bean
    public FilterRegistrationBean<TrustedInternalRequestFilter> trustedInternalRequestFilterRegistrationBean(
            @Value("${internal.auth.secret:change-me-in-prod}") String internalAuthSecret,
            @Value("${internal.auth.max-skew-seconds:300}") long maxSkewSeconds) {
        TrustedInternalRequestFilter filter = new TrustedInternalRequestFilter(internalAuthSecret, maxSkewSeconds);
        FilterRegistrationBean<TrustedInternalRequestFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Integer.MIN_VALUE);
        return bean;
    }
}
