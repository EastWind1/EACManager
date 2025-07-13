package pers.eastwind.billmanager.service;

import lombok.Getter;
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
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 附件服务
 */
@Slf4j
@Service
public class AttachmentService implements InitializingBean {
    /**
     * 根目录
     */
    @Getter
    private Path rootPath;
    /**
     * 临时目录
     */
    @Getter
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
        String TEMP_DIR = properties.getAttachment().getTemp();
        tempPath = rootPath.resolve(TEMP_DIR).normalize().toAbsolutePath();
        if (rootPath.startsWith(tempPath)) {
            throw new RuntimeException("附件目录不能在临时目录内");
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
     * 获取绝对路径
     *
     * @param relativePath 相对路径
     * @return 绝对路径
     */
    public Path getAbsolutePath(Path relativePath) {
        if (relativePath == null) {
            throw new RuntimeException("路径不能为空");
        }
        // 去除头部的斜杠
        if (relativePath.startsWith("/")) {
            String pathStr = relativePath.toString().substring(1);
            relativePath = Path.of(pathStr);
        }
        return rootPath.resolve(relativePath).normalize().toAbsolutePath();
    }

    /**
     * 校验路径
     * 禁止使用根路径外的路径
     */
    void validPath(Path path) {
        if (path == null) {
            throw new RuntimeException("路径为空");
        }
        if (!path.startsWith(rootPath)) {
            throw new RuntimeException("禁止使用外部路径");
        }
    }

    /**
     * 判断是否是临时文件
     */
    public boolean isTempFile(Path path) {
        validPath(path);
        return path.startsWith(tempPath);
    }

    /**
     * 创建文件
     */
    public Path createFile(Path path) {
        validPath(path);
        if (Files.exists(path)) {
            throw new RuntimeException("文件已存在");
        }
        if (!Files.exists(path.getParent())) {
            createDirectory(path.getParent());
        }
        try {
            Files.createFile(path);
        } catch (IOException e) {
            throw new RuntimeException("创建文件失败" + path, e);
        }
        return path;
    }

    /**
     * 创建文件夹
     */
    public Path createDirectory(Path path) {
        validPath(path);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException("创建文件夹失败: " + path, e);
            }
        }
        return path;
    }

    /**
     * 获取文件类型
     *
     * @param bytes 文件字节
     * @return 文件类型
     */
    private AttachmentType getFileType(byte[] bytes, String fileName) {
        Tika tika = new Tika();
        String mimeType = tika.detect(bytes);
        return switch (mimeType) {
            case "application/pdf" -> AttachmentType.PDF;
            case "image/jpeg", "image/png", "image/gif" -> AttachmentType.IMAGE;
            case "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    AttachmentType.WORD;
            case "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    AttachmentType.EXCEL;
            case "application/zip" -> {
                if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                    yield AttachmentType.EXCEL;
                } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                    yield AttachmentType.WORD;
                } else {
                    yield AttachmentType.OTHER;
                }
            }
            default -> throw new RuntimeException("不支持的文件类型: " + mimeType);
        };
    }

    /**
     * 提取 PDF 为图片并保存至临时文件
     */
    public Path renderPDFToImage(Path path) {
        validPath(path);
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            Path imagePath = null;
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImage(page);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ImageIO.write(image, "png", os);
                imagePath = tempPath.resolve("PDFImage-" + System.currentTimeMillis() + ".png");
                Files.copy(new ByteArrayInputStream(os.toByteArray()), imagePath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (imagePath != null) {
                return imagePath;
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
     * @param path 目录路径
     * @return 附件实体
     */
    private Attachment saveFile(MultipartFile file, Path path) {
        validPath(path);
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
        validPath(path);
        if (bytes == null) {
            throw new IllegalArgumentException("禁止上传空文件");
        }

        if (fileName == null || fileName.isEmpty()) {
            throw new RuntimeException("文件名不能为空");
        }

        Path targetPath = path.resolve(fileName);
        validPath(targetPath);
        AttachmentType type;
        try {
            type = getFileType(bytes, fileName);
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
     * @param path 目录路径
     * @return 附件实体
     */
    @Transactional
    public AttachmentDTO upload(MultipartFile file, Path path) {
        return attachmentMapper.toAttachmentDTO(attachmentRepository.save(saveFile(file, path)));
    }

    /**
     * 上传至临时文件目录
     *
     * @param file 文件
     * @return 附件实体
     */
    public AttachmentDTO uploadTemp(MultipartFile file) {
        return attachmentMapper.toAttachmentDTO(saveFile(file, tempPath));
    }

    /**
     * 读取文件
     *
     * @param path 文件路径
     * @return 文件资源
     */
    public Resource loadByPath(Path path) {
        validPath(path);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new RuntimeException("文件不存在");
        }
        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            throw new RuntimeException("文件地址不合法", e);
        }
        return resource;
    }

    /**
     * 移动文件或文件夹
     *
     * @param origin 原始文件或文件夹相对路径
     * @param target 目标文件夹相对路径
     */
    public void move(Path origin, Path target) {
        copy(origin, target, true);
        delete(origin);
    }

    /**
     * 复制文件或文件夹
     *
     * @param origin      原始文件或文件夹路径，若为文件夹，则复制所有子文件
     * @param target      目标文件夹路径
     * @param includeSelf 若为文件夹时，是否复制自身
     */
    public void copy(Path origin, Path target, boolean includeSelf) {
        validPath(origin);
        validPath(target);
        if (!Files.exists(origin)) {
            throw new RuntimeException("找不到文件或文件夹");
        }
        if (!Files.exists(target)) {
            createDirectory(target);
        } else if (!Files.isDirectory(target)) {
            throw new RuntimeException("目标必须是文件夹");
        }

        if (Files.isDirectory(origin)) {
            try (Stream<Path> stream = Files.walk(origin)) {
                stream.forEach(path -> {
                    Path targetPath = includeSelf ? target.resolve(origin.getParent().relativize(path)) : target.resolve(origin.relativize(path));
                    try {
                        if (Files.isDirectory(path)) {
                            if (!Files.exists(targetPath)) {
                                Files.createDirectories(targetPath);
                            }
                        } else {
                            Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("复制文件失败: " + path, e);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("复制文件失败", e);
            }
        } else {
            try {
                Path targetPath = target.resolve(origin.getFileName());
                Files.copy(origin, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("复制文件失败: " + origin, e);
            }
        }
    }

    /**
     * 删除文件或文件夹
     *
     * @param path 相对路径
     */
    public void delete(Path path) {
        validPath(path);
        if (path.equals(rootPath)) {
            throw new RuntimeException("不能删除根目录");
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream
                    .sorted(Comparator.reverseOrder())
                    .forEach(curPath -> {
                        try {
                            Files.delete(curPath);
                        } catch (IOException e) {
                            throw new RuntimeException("删除文件失败: " + path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("删除文件失败", e);
        }
    }

    /**
     * 压缩文件或文件夹
     *
     * @param sourceDirPath 源路径
     * @param zipFilePath   目标路径
     */
    public Path zip(Path sourceDirPath, Path zipFilePath) {
        validPath(sourceDirPath);
        validPath(zipFilePath);

        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            if (!Files.exists(zipFilePath)) {
                Files.createFile(zipFilePath);
            }
            if (Files.isDirectory(sourceDirPath)) {
                try (Stream<Path> stream = Files.walk(sourceDirPath)) {
                    stream.filter(path -> !Files.isDirectory(path))
                            .forEach(path -> {
                                String name = sourceDirPath.relativize(path).toString().replace("\\", "/");
                                ZipEntry zipEntry = new ZipEntry(name);
                                try {
                                    zipOut.putNextEntry(zipEntry);
                                    Files.copy(path, zipOut);
                                    zipOut.closeEntry();
                                } catch (IOException e) {
                                    throw new RuntimeException("压缩文件失败: " + path, e);
                                }
                            });
                }
            } else {
                String name = sourceDirPath.getFileName().toString();
                ZipEntry zipEntry = new ZipEntry(name);
                zipOut.putNextEntry(zipEntry);
                Files.copy(sourceDirPath, zipOut);
                zipOut.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("压缩文件失败", e);
        }
        return zipFilePath;
    }

    /**
     * 清理临时文件
     */
    @PreDestroy
    public void cleanTemp() {
        delete(tempPath);
    }
}
