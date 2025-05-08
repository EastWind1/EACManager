package com.eastwind.EACAfterSaleMgr.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 附件服务
 */
@Slf4j
@Service
public class AttachmentService implements InitializingBean {

    @Value("${attachment.path}")
    private String ROOT_DIR;
    @Value("${attachment.temp}")
    private String TEMP_DIR;
    /**
     * 根目录
     */
    private Path rootPath;
    /**
     * 临时目录
     */
    private Path tempPath;

    /**
     * 初始化附件目录
     */
    @Override
    public void afterPropertiesSet() {
        rootPath = Path.of(ROOT_DIR).normalize().toAbsolutePath();
        tempPath = Path.of(TEMP_DIR).normalize().toAbsolutePath();
        if (rootPath.equals(tempPath)) {
            throw new RuntimeException("临时目录不能与附件目录相同");
        }
        if (!tempPath.startsWith(rootPath)) {
            throw new RuntimeException("临时目录必须位于附件目录内");
        }
        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
            } catch (IOException e) {
                throw new RuntimeException("创建附件目录失败", e);
            }
        }

        if (!Files.exists(tempPath)) {
            try {
                Files.createDirectories(tempPath);
            } catch (IOException e) {
                throw new RuntimeException("创建临时目录失败", e);
            }
        }
    }

    /**
     * 检查文件类型是否合法
     *
     * @param inputStream 文件
     */
    public void checkFileType(InputStream inputStream) {
        // 使用 ImageIO 判断是否为图片类型
        try (InputStream is = inputStream) {
            // 图片
            if (ImageIO.read(is) != null) {
                return;
            }

            byte[] header = new byte[8];
            int size = is.read(header);
            if (size < 0) {
                throw new RuntimeException("文件为空");
            }
            // pdf
            if ((header[0] == (byte) 0x25 && header[1] == (byte) 0x50 &&
                    header[2] == (byte) 0x44 && header[3] == (byte) 0x46)) {
            } else if ((header[0] == (byte) 0x50 && header[1] == (byte) 0x4B &&
                    header[2] == (byte) 0x03 && header[3] == (byte) 0x04)) {
                // Excel 或 Word
            } else {
                throw new RuntimeException("文件类型不合法");
            }
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }

    }

    /**
     * 上传文件
     *
     * @param file    文件
     * @param pathStr 相对附件目录路径，默认值为空
     * @param isTemp  是否临时目录
     * @return 文件相对路径
     */
    public Path upload(MultipartFile file, String pathStr, boolean isTemp) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }
        if (file.getOriginalFilename() == null) {
            throw new RuntimeException("文件名不能为空");
        }
        if (pathStr == null) {
            pathStr = "";
        }
        Path targetPath = (isTemp ? tempPath : rootPath).resolve(pathStr).resolve(file.getOriginalFilename()).normalize().toAbsolutePath();
        if (!targetPath.startsWith(isTemp ? tempPath : rootPath)) {
            throw new RuntimeException("禁止使用外部路径");
        }

        try {
            checkFileType(file.getInputStream());
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败", e);
        }

        return rootPath.relativize(targetPath);
    }

    /**
     * 获取绝对路径
     * 只允许项目内调用
     *
     * @param relativePath 相对路径
     * @return 绝对路径
     */
    Path getAbsolutePath(Path relativePath) {
        return rootPath.resolve(relativePath);
    }

    /**
     * 读取文件
     *
     * @param fileName 文件名
     * @return 文件资源
     */
    public Resource loadAsResource(String fileName) {
        Resource resource = null;
        try {
            resource = new UrlResource(Path.of(ROOT_DIR, fileName).toUri());
        } catch (MalformedURLException e) {
            throw new RuntimeException("解析文件地址错误", e);
        }
        if (resource.exists() || resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("找不到文件");
        }
    }

    /**
     * 移动文件
     *
     * @param originRelativePath 原始文件相对路径
     * @param targetRelativePath 目标文件相对路径
     */
    public void move(Path originRelativePath, Path targetRelativePath) {
        Path origin = rootPath.resolve(originRelativePath);
        Path target = rootPath.resolve(targetRelativePath);
        if (!Files.exists(origin)) {
            throw new RuntimeException("找不到文件");
        }

        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target);
            }
            Files.move(origin, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("移动文件失败", e);
        }
    }

    /**
     * 清理临时文件
     */
    @PreDestroy
    public void cleanTemp() {
        try (Stream<Path> stream = Files.walk(tempPath)) {
            stream
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.error("清理临时文件失败:{} {}", path.toString(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("清理临时文件失败", e);
        }
    }
}
