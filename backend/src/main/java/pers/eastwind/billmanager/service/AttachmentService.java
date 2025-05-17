package pers.eastwind.billmanager.service;

import pers.eastwind.billmanager.config.ConfigProperties;
import pers.eastwind.billmanager.model.common.AttachmentType;
import pers.eastwind.billmanager.model.dto.AttachmentDTO;
import pers.eastwind.billmanager.model.entity.Attachment;
import pers.eastwind.billmanager.model.mapper.AttachmentMapper;
import pers.eastwind.billmanager.repository.AttachmentRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    /**
     * 临时路径
     */
    public String TEMP_DIR;
    /**
     * 根目录
     */
    private Path rootPath;
    /**
     * 临时目录
     */
    private Path tempPath;
    private final ConfigProperties properties;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper attachmentMapper;

    public AttachmentService(ConfigProperties properties, AttachmentRepository attachmentRepository, AttachmentMapper attachmentMapper) {
        this.properties = properties;
        this.attachmentRepository = attachmentRepository;
        this.attachmentMapper = attachmentMapper;
    }

    /**
     * 初始化附件目录
     */
    @Override
    public void afterPropertiesSet() {
        rootPath = properties.getAttachment().getPath().normalize().toAbsolutePath();
        TEMP_DIR = properties.getAttachment().getTemp();
        tempPath = rootPath.resolve(TEMP_DIR).normalize().toAbsolutePath();
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
     * 获取文件类型
     *
     * @param inputStream 文件输入流
     * @return 文件类型
     */
    private AttachmentType getFileType(InputStream inputStream) {
        Tika tika = new Tika();
        try {
            String mimeType = tika.detect(inputStream);
            return switch (mimeType) {
                case "application/pdf" -> AttachmentType.PDF;
                case "image/jpeg", "image/png", "image/gif" -> AttachmentType.IMAGE;
                case "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        AttachmentType.WORD;
                case "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                        AttachmentType.EXCEL;
                default -> throw new RuntimeException("不支持的文件类型: " + mimeType);
            };
        } catch (IOException e) {
            throw new RuntimeException("读取文件流失败", e);
        }
    }

    /**
     * 保存文件
     *
     * @param file 文件
     * @param path 相对附件目录路径
     * @return 附件实体
     */
    private Attachment saveFile(MultipartFile file, Path path) {
        if (path == null) {
            throw new RuntimeException("路径不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }
        if (file.getOriginalFilename() == null) {
            throw new RuntimeException("文件名不能为空");
        }
        Path targetPath = rootPath.resolve(path).resolve(file.getOriginalFilename()).normalize().toAbsolutePath();
        if (!targetPath.startsWith(rootPath)) {
            throw new RuntimeException("禁止使用外部路径");
        }

        AttachmentType type;
        try {
            type = getFileType(file.getInputStream());
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败", e);
        }
        Path relativePath = rootPath.relativize(targetPath);
        Attachment attachment = new Attachment();
        attachment.setName(path.getFileName().toString());
        attachment.setType(type);
        attachment.setRelativePath(relativePath.toString());

        return attachment;
    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @param path 相对附件目录路径
     * @return 附件实体
     */
    @Transactional
    public AttachmentDTO upload(MultipartFile file, Path path) {
        return attachmentMapper.toAttachmentDTO(attachmentRepository.save(saveFile(file, path)));
    }

    /**
     * 上传临时文件
     *
     * @param file 文件
     * @return 附件实体
     */
    public AttachmentDTO uploadTemp(MultipartFile file) {
        return attachmentMapper.toAttachmentDTO(saveFile(file, Path.of(TEMP_DIR)));
    }

    /**
     * 获取绝对路径
     * 只允许包内调用
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
     * @param path 文件路径
     * @return 文件资源
     */
    public Resource loadByPath(Path path) {
        if (path == null) {
            throw new RuntimeException("文件路径为空");
        }
        Path absolutePath = rootPath.resolve(path).normalize().toAbsolutePath();
        if (!absolutePath.startsWith(rootPath)) {
            throw new RuntimeException("禁止使用外部路径");
        }
        if (!Files.exists(absolutePath) || Files.isDirectory(absolutePath)) {
            throw new RuntimeException("文件不存在");
        }
        Resource resource;
        try {
            resource = new UrlResource(absolutePath.toUri());
        } catch (MalformedURLException e) {
            throw new RuntimeException("文件地址不合法", e);
        }
        return resource;
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
                            log.error("清理临时文件失败:{} {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("清理临时文件失败", e);
        }
    }
}
