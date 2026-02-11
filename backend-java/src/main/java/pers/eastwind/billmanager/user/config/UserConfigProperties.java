package pers.eastwind.billmanager.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 用户模块配置
 */
@Configuration
@ConfigurationProperties("config.jwt")
@Data
public class UserConfigProperties {
    /**
     * JWT 密钥
     */
    private String key;
    /**
     * JWT 过期时间
     */
    private long expire;
}
