package pers.eastwind.billmanager.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import pers.eastwind.billmanager.user.model.User;
import pers.eastwind.billmanager.user.util.AuthUtil;

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
    public AuditorAware<Integer> auditorProvider() {
        User user = AuthUtil.getCurUser();
        return () -> Optional.ofNullable(user).map(User::getId);
    }
}
