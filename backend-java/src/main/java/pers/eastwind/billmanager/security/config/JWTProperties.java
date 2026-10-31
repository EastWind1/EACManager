package pers.eastwind.billmanager.security.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
@ConfigurationProperties("config.jwt")
@Data
public class JWTProperties {
    private String secret;
    /**
     * 过期时间，单位秒
     */
    private long expire;


    @PostConstruct
    void validateKey() {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("config.jwt.secret 未配置或不足 32 字节，请通过 JWT_KEY 环境变量注入");
        }
    }
}
