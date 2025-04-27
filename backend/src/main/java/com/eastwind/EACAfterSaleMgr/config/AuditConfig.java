package com.eastwind.EACAfterSaleMgr.config;

import com.eastwind.EACAfterSaleMgr.model.entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 审计配置
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider")
public class AuditConfig {
    @Bean
    public AuditorAware<User> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .map(user -> {
                    // 单独处理匿名用户
                    if ("anonymousUser".equals(user)) {
                        return null;
                    }
                    return (User) user;
                });
    }

    /**
     * 自定义 DateTimeProvider
     * 默认的 DateTimeProvider 只会提供 LocalDateTime
     */
    @Bean
    public DateTimeProvider zonedDateTimeProvider() {
        return () -> Optional.of(ZonedDateTime.now());
    }
}
