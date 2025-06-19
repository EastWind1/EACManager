package pers.eastwind.billmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 读取配置文件
 */
@Configuration
@ConfigurationProperties(prefix = "config")
@Data
public class ConfigProperties {
    /**
     * JWT 配置
     */
    private Jwt jwt;

    /**
     * 附件路径相关配置
     */
    private Attachment attachment;
    /**
     * 映射规则路径
     */
    private Path mapRulePath;

    @Data
    public static class Jwt {
        /**
         * 密钥（base64 编码）
         */
        private String key;
    }

    @Data
    public static class Attachment {
        /**
         * 存储附件的根目录（必须是绝对路径）
         */
        private Path path;

        /**
         * 临时文件目录（相对于 path）
         */
        private String temp;
    }
}