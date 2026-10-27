package pers.eastwind.billmanager.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.util.AuthUtil;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class AuditConfig {
    /**
     * 审计字段的操作人取自当前登录用户，无登录上下文（如启动任务）时为空
     */
    @Bean
    public AuditorAware<Integer> auditorProvider() {
        return () -> Optional.ofNullable(AuthUtil.getCurUser()).map(AuthorityUser::getId);
    }
}
