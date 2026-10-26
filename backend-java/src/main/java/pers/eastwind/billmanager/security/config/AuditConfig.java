package pers.eastwind.billmanager.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.util.AuthUtil;

import java.util.Optional;

/**
 * 审计配置
 */
@Configuration
@EnableJpaAuditing
public class AuditConfig {
    @Bean
    public AuditorAware<Integer> auditorProvider() {
        return () -> Optional.ofNullable(AuthUtil.getCurUser()).map(AuthorityUser::getId);
    }
}
