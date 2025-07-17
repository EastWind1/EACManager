package pers.eastwind.billmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import pers.eastwind.billmanager.model.entity.User;

import java.util.Optional;

/**
 * 审计配置
 */
@Configuration
@EnableJpaAuditing
public class AuditConfig {
    /**
     * 审计用户配置
     */
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
}
