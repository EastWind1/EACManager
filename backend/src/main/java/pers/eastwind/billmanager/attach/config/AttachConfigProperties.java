package pers.eastwind.billmanager.attach.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 附件模块配置
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "config.attachment")
public class AttachConfigProperties {
    /**
     * 存储附件的根目录（必须是绝对路径）
     */
    private Path path;

    /**
     * 临时文件目录（相对于 path）
     */
    private String temp;
}
