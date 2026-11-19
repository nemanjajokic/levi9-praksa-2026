package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.infrastructure.config.ClockConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * Puts a rate limit on register, per client IP (see {@link RateLimitInterceptor}). Login's is a filter in
 * {@link SecurityConfig}: form login never reaches MVC.
 * <p>
 * Imports {@link ClockConfig} itself, because {@code @WebMvcTest} slices load this class but not the clock.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
@Import(ClockConfig.class)
class RateLimitConfig implements WebMvcConfigurer {
    private final RateLimitProperties limits;
    private final Clock clock;

    RateLimitConfig(RateLimitProperties limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(new RateLimiter(limits.register(), clock)))
                .addPathPatterns("/api/auth/register");
    }
}
