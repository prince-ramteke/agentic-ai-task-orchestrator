package com.prince.agentic.common.observability;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wiring for the Milestone 10 request-correlation filter (ADR-0030). Registered at
 * {@link Ordered#HIGHEST_PRECEDENCE} so the {@code requestId} MDC key is populated before Spring
 * Security's filters run — meaning authentication/authorization log lines already carry the id.
 * Coverage-excluded infrastructure (mirrors {@code AuditConfig}/{@code AgentConfig}).
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> reg = new FilterRegistrationBean<>(new RequestIdFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        reg.setName("requestIdFilter");
        return reg;
    }
}
