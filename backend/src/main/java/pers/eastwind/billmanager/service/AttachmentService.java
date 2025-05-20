package pers.eastwind.billmanager.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private String TEMP_DIR;
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
     * 判断是否是临时文件
     */
    public boolean isTempFile(Path path) {

        return getAbsolutePath(path).startsWith(tempPath);
    }

    /**
     * 获取文件类型
     *
     * @param bytes 文件字节
     * @return 文件类型
     */
    private AttachmentType getFileType(byte[] bytes) {
        Tika tika = new Tika();
        String mimeType = tika.detect(bytes);
        return switch (mimeType) {
            case "application/pdf" -> AttachmentType.PDF;
            case "image/jpeg", "image/png", "image/gif" -> AttachmentType.IMAGE;
            case "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    AttachmentType.WORD;
            case "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    AttachmentType.EXCEL;
            default -> throw new RuntimeException("不支持的文件类型: " + mimeType);
        };
    }

    /**
     * 获取绝对路径
     * 只允许包内调用
     *
     * @param relativePath 相对路径
     * @return 绝对路径
     */
    Path getAbsolutePath(Path relativePath) {
        if (relativePath == null) {
            throw new RuntimeException("路径不能为空");
        }
        // 去除头部的斜杠
        relativePath = relativePath.startsWith("/") ? relativePath.subpath(1, relativePath.getNameCount()) : relativePath;
        Path targetPath = rootPath.resolve(relativePath).normalize().toAbsolutePath();
        if (!targetPath.startsWith(rootPath)) {
            throw new RuntimeException("禁止使用外部路径");
        }

        return rootPath.resolve(relativePath);
    }

    /**
     * 判断 PDF 是否是扫描件
     */
    public boolean isScannedPdf(Path path) {
        Path absolutePath = getAbsolutePath(path);
        try (PDDocument document = Loader.loadPDF(absolutePath.toFile())) {
            for (PDPage page : document.getPages()) {
                String text = new PDFTextStripper().getText(document);
                if (text.trim().isEmpty()) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("读取 PDF 失败", e);
        }
        return false;
    }

    /**
     * 提取 PDF 中的图片并保存至临时文件
     */
    public AttachmentDTO extractImages(Path path) {
        Path absolutePath = getAbsolutePath(path);
        try (PDDocument document = Loader.loadPDF(absolutePath.toFile())) {
            Attachment attachment = null;
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImage(page);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ImageIO.write(image, "png", os);
                attachment = saveFile(os.toByteArray(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".png", Path.of(TEMP_DIR));
            }
            if (attachment != null) {
                return attachmentMapper.toAttachmentDTO(attachment);
            } else {
                throw new RuntimeException("提取 PDF 图片失败");
            }
        } catch (IOException e) {
            throw new RuntimeException("提取 PDF 图片失败", e);
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
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }

        if (file.getOriginalFilename() == null) {
            throw new RuntimeException("文件名不能为空");
        }

        try {
            return saveFile(file.getBytes(), file.getOriginalFilename(), path);
        } catch (IOException e) {
            throw new RuntimeException("获取文件流失败", e);
        }
    }

    /**
     * 保存文件
     *
     * @param bytes    文件字节
     * @param fileName 文件名称
     * @param path     相对附件目录路径
     * @return 附件实体
     */
    private Attachment saveFile(byte[] bytes, String fileName, Path path) {
        if (bytes == null) {
            throw new IllegalArgumentException("禁止上传空文件");
        }

        if (fileName == null || fileName.isEmpty()) {
            throw new RuntimeException("文件名不能为空");
        }

        Path targetPath = getAbsolutePath(path.resolve(fileName));

        AttachmentType type;
        try {
            type = getFileType(bytes);
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }
            Files.copy(new ByteArrayInputStream(bytes), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败", e);
        }
        Path relativePath = rootPath.relativize(targetPath);
        Attachment attachment = new Attachment();
        attachment.setName(fileName);
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
     * 读取文件
     *
     * @param path 文件路径
     * @return 文件资源
     */
    public Resource loadByPath(Path path) {
        Path absolutePath = getAbsolutePath(path);
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
