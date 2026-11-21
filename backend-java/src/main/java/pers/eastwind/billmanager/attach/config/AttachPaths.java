package pers.eastwind.billmanager.attach.config;

import org.springframework.stereotype.Component;
import pers.eastwind.billmanager.common.exception.FileOpException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 附件存储路径：根目录与固定临时目录只在这里解析和创建，
 * 其他组件通过注入获取，避免各自拼路径或依赖静态工具类。
 */
@Component
public class AttachPaths {
    /**
     * 临时文件目录，与业务 namespace 隔离（namespace 不允许包含点）
     */
    public static final String TEMP_DIR_NAME = ".tmp";

    private final Path root;
    private final Path temp;

    public AttachPaths(AttachConfigProperties properties) {
        this.root = properties.getPath().normalize().toAbsolutePath();
        this.temp = root.resolve(TEMP_DIR_NAME);
        try {
            Files.createDirectories(root);
            Files.createDirectories(temp);
        } catch (IOException e) {
            throw new FileOpException("创建附件目录失败", e);
        }
    }

    public Path root() {
        return root;
    }

    public Path temp() {
        return temp;
    }
}
