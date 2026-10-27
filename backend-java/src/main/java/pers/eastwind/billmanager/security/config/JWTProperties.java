package pers.eastwind.billmanager.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("config.jwt")
@Data
public class JWTProperties {
    private String key;
    /**
     * 过期时间，单位秒
     */
    private long expire;
}
