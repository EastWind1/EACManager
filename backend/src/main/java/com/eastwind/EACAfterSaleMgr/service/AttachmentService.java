package com.eastwind.EACAfterSaleMgr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 附件服务
 */
@Slf4j
@Service
public class AttachmentService implements InitializingBean {

    @Value("${attachment.path}")
    public String ROOT_DIR;
    @Value("${attachment.temp}")
    public String TEMP_DIR;

    /**
     * 初始化附件目录
     */
    @Override
    public void afterPropertiesSet() {
        Thread.startVirtualThread(() -> {
            Path rootPath = Path.of(ROOT_DIR).normalize().toAbsolutePath();
            if (!Files.exists(rootPath)) {
                try {
                    Files.createDirectories(rootPath);
                } catch (IOException e) {
                    throw new RuntimeException("创建附件目录失败", e);
                }
            }
            Path tempPath = Path.of(TEMP_DIR).normalize().toAbsolutePath();
            if (!Files.exists(tempPath)) {
                try {
                    Files.createDirectories(tempPath);
                } catch (IOException e) {
                    throw new RuntimeException("创建临时目录失败", e);
                }
            }
        });
    }
    /**
     * 上传文件
     *
     * @param file 文件
     * @return 文件路径
     */
    public Path upload(MultipartFile file, String path) {
        if (path == null || path.isEmpty()) {
            path = TEMP_DIR;
        } else {
            if (!path.startsWith(ROOT_DIR)) {
                throw new RuntimeException("禁止上传到其他目录");
            }
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }
        if (file.getOriginalFilename() == null) {
            throw new RuntimeException("文件名不能为空");
        }
        Path targetPath = Path.of(path, file.getOriginalFilename()).normalize().toAbsolutePath();
        if (!Files.exists(targetPath)) {
            try {
                Files.createFile(targetPath);
            } catch (IOException e){
                throw new RuntimeException("创建文件失败", e);
            }
        }
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("上传文件失败", e);
        }
        return targetPath;
    }
    /**
     * 上传临时文件
     */
    public Path uploadTemp(MultipartFile file) {
        return upload(file, TEMP_DIR);
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
     */
    public void move(String originPath, String targetPath) {
        if (originPath == null || targetPath == null || originPath.isEmpty() || targetPath.isEmpty()) {
            throw new RuntimeException("路径不能为空");
        }
        if (!originPath.startsWith(ROOT_DIR) || !targetPath.startsWith(ROOT_DIR)) {
            throw new RuntimeException("禁止移动到其他目录");
        }
        Path origin = Path.of(originPath).normalize().toAbsolutePath();
        Path target = Path.of(targetPath).normalize().toAbsolutePath();
        if (!Files.exists(origin)) {
            throw new RuntimeException("找不到文件");
        }
        try {
            Files.move(origin, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("移动文件失败", e);
        }
    }
}
