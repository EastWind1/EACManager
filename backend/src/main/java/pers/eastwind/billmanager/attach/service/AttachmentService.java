package pers.eastwind.billmanager.attach.service;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pers.eastwind.billmanager.attach.config.AttachConfigProperties;
import pers.eastwind.billmanager.attach.model.*;
import pers.eastwind.billmanager.attach.repository.AttachmentRepository;
import pers.eastwind.billmanager.attach.repository.BillAttachRelationRepository;
import pers.eastwind.billmanager.common.exception.BizException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 附件服务
 */
@Slf4j
@Service
public class AttachmentService implements InitializingBean {
    private final AttachConfigProperties properties;
    private final AttachmentRepository attachmentRepository;
    private final BillAttachRelationRepository billAttachRelationRepository;
    private final AttachmentMapper attachmentMapper;
    private final TransactionTemplate transactionTemplate;
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

    public AttachmentService(AttachConfigProperties properties, AttachmentRepository attachmentRepository, BillAttachRelationRepository billAttachRelationRepository, AttachmentMapper attachmentMapper, TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.attachmentRepository = attachmentRepository;
        this.billAttachRelationRepository = billAttachRelationRepository;
        this.attachmentMapper = attachmentMapper;
        this.transactionTemplate = transactionTemplate;
    }


    /**
     * 初始化附件目录
     */
    @Override
    public void afterPropertiesSet() {
        rootPath = properties.getPath().normalize().toAbsolutePath();
        String TEMP_DIR = properties.getTemp();
        this.tempPath = rootPath.resolve(TEMP_DIR).normalize().toAbsolutePath();
        if (rootPath.startsWith(this.tempPath)) {
            throw new BizException("附件目录不能在临时目录内");
        }
        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
            } catch (IOException e) {
                throw new BizException("创建附件目录失败", e);
            }
        }

        if (!Files.exists(tempPath)) {
            try {
                Files.createDirectories(tempPath);
            } catch (IOException e) {
                throw new BizException("创建临时目录失败", e);
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
            throw new BizException("路径不能为空");
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
    private void validPath(Path path) {
        if (path == null) {
            throw new BizException("路径为空");
        }
        if (!path.startsWith(rootPath)) {
            throw new BizException("禁止使用外部路径");
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
            throw new BizException("文件已存在");
        }
        if (!Files.exists(path.getParent())) {
            createDirectory(path.getParent());
        }
        try {
            Files.createFile(path);
        } catch (IOException e) {
            throw new BizException("创建文件失败", e);
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
                throw new BizException("创建文件夹失败", e);
            }
        }
        return path;
    }

    /**
     * 获取文件类型
     *
     * @param path 文件路径
     * @return 文件类型
     */
    private AttachmentType getFileType(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
        String fileName = path.getFileName().toString();
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        return switch (mimeType) {
            case "application/pdf" -> AttachmentType.PDF;
            case "image/jpeg", "image/png", "image/gif" -> AttachmentType.IMAGE;
            case "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    AttachmentType.WORD;
            case "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    AttachmentType.EXCEL;
            // 禁止可执行文件
            case "application/x-msdownload", "application/x-executable", "application/x-sh", "application/x-bat" ->
                    throw new BizException("不支持的文件类型: " + mimeType);
            default -> AttachmentType.OTHER;
        };
    }

    /**
     * 提取 PDF 为图片并保存至临时文件
     * 暂时只支持转换第一页
     */
    public Path renderPDFToImage(Path path) {
        validPath(path);
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.getNumberOfPages() <= 0) {
                throw new BizException("PDF 文件为空");
            }
            Path imagePath = tempPath.resolve("PDFImage-" + System.currentTimeMillis() + ".png");;
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImage(0);
            ImageIO.write(image, "png", imagePath.toFile());
            return imagePath;
        } catch (IOException e) {
            throw new BizException("提取 PDF 图片失败", e);
        }
    }

    /**
     * 上传文件
     *
     * @param resources 文件资源
     * @param path      相对附件目录路径
     * @return 附件实体列表
     */
    public List<Attachment> upload(List<Resource> resources, Path path) {
        validPath(path);
        if (resources == null || resources.isEmpty()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }
        List<Attachment> attachments = new ArrayList<>();
        for (Resource resource : resources) {
            attachments.add(uploadSingle(resource, path));
        }
        return attachments;
    }

    /**
     * 上传单个文件
     *
     * @param resource 文件资源
     * @param path     相对附件目录路径
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    private Attachment uploadSingle(Resource resource, Path path) {
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }

        String fileName = resource.getFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        Path targetPath = path.resolve(fileName);
        validPath(targetPath);
        AttachmentType type;
        try {
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }
            Files.copy(resource.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            // 文件类型判断只能在保存文件之后
            type = getFileType(targetPath);
        } catch (IOException e) {
            // 尝试删除该临时文件
            try {
                Files.delete(targetPath);
            } catch (IOException ignored) {
            }
            throw new BizException("保存文件失败", e);
        }
        Path relativePath = rootPath.relativize(targetPath);
        Attachment attachment = new Attachment();
        attachment.setName(fileName);
        attachment.setType(type);
        attachment.setRelativePath(relativePath.toString());

        return attachment;
    }

    /**
     * 上传临时文件
     *
     * @param resources 文件资源
     * @return 附件实体列表
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public List<Attachment> uploadTemp(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }
        List<Attachment> attachments = new ArrayList<>();
        for (Resource resource : resources) {
            attachments.add(uploadSingle(resource, tempPath));
        }
        return attachments;
    }

    /**
     * 读取文件
     *
     * @param path 文件路径
     * @return 文件资源
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'FINANCE')")
    public Resource loadByPath(Path path) {
        validPath(path);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new BizException("文件不存在");
        }
        return new FileSystemResource(path);
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
     * @param copyRootDir 若为文件夹时，是否复制源文件夹，以保持目录结构。当源是文件时不生效
     */
    public void copy(Path origin, Path target, boolean copyRootDir) {
        validPath(origin);
        validPath(target);
        if (!Files.exists(origin)) {
            throw new BizException("找不到文件或文件夹" + origin);
        }
        if (!Files.exists(target)) {
            createDirectory(target);
        } else if (!Files.isDirectory(target)) {
            throw new BizException("目标必须是文件夹");
        }
        // 源为文件夹
        if (Files.isDirectory(origin)) {
            try (Stream<Path> stream = Files.walk(origin)) {
                stream.forEach(path -> {
                    Path targetPath = copyRootDir ? target.resolve(origin.getParent().relativize(path)) : target.resolve(origin.relativize(path));
                    try {
                        if (Files.isDirectory(path)) {
                            if (!Files.exists(targetPath)) {
                                Files.createDirectories(targetPath);
                            }
                        } else {
                            Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new BizException("复制文件失败", e);
                    }
                });
            } catch (IOException e) {
                throw new BizException("读取源文件夹失败", e);
            }
        } else {
            try {
                Path targetPath = target.resolve(origin.getFileName());
                Files.copy(origin, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new BizException("复制文件失败: " + origin, e);
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
            throw new BizException("不能删除根目录");
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(curPath -> {
                try {
                    Files.delete(curPath);
                } catch (IOException e) {
                    throw new BizException("删除文件失败", e);
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
     * @param zipFilePath   生成压缩包目标路径
     */
    public void zip(Path sourceDirPath, Path zipFilePath) {
        validPath(sourceDirPath);
        validPath(zipFilePath);

        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            if (!Files.exists(zipFilePath)) {
                Files.createFile(zipFilePath);
            }
            if (Files.isDirectory(sourceDirPath)) {
                try (Stream<Path> stream = Files.walk(sourceDirPath)) {
                    stream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                        String name = sourceDirPath.relativize(path).toString().replace("\\", "/");
                        ZipEntry zipEntry = new ZipEntry(name);
                        try {
                            zipOut.putNextEntry(zipEntry);
                            Files.copy(path, zipOut);
                            zipOut.closeEntry();
                        } catch (IOException e) {
                            throw new BizException("压缩文件失败", e);
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
            throw new BizException("压缩文件失败", e);
        }
    }

    /**
     * 获取业务单据附件
     *
     * @param billId   单据 ID
     * @param billType 单据类型
     * @return 业务单据附件
     */
    public List<Attachment> getByBill(Integer billId, BillType billType) {
        if (billType == null) {
            throw new BizException("单据类型不能为空");
        }
        if (billId == null) {
            throw new BizException("单据 ID 不能为空");
        }
        return attachmentRepository.findByBill(billId, billType);
    }

    /**
     * 根据目标附件集合更新业务单据关联附件
     *
     * @param billId         单据 ID
     * @param billNumber     单据编号
     * @param billType       单据类型
     * @param attachmentDTOs 目标附件集合
     * @return 更新后的附件集合
     */
    @Transactional
    public List<Attachment> updateRelativeAttach(Integer billId, String billNumber, BillType billType, List<AttachmentDTO> attachmentDTOs) {

        List<BillAttachRelation> billAttachRelations = billAttachRelationRepository.findByBillIdAndBillType(billId, billType);
        Set<Integer> removeIds = new HashSet<>();
        for (BillAttachRelation billAttachRelation : billAttachRelations) {
            removeIds.add(billAttachRelation.getAttachId());
        }

        for (AttachmentDTO attachmentDTO : attachmentDTOs) {
            // 新增
            if (attachmentDTO.getId() == null) {
                Attachment newAttachment = attachmentMapper.toEntity(attachmentDTO);
                // 移动临时文件
                Path origin = getAbsolutePath(Path.of(newAttachment.getRelativePath()));
                if (isTempFile(origin)) {
                    Path targetDirRelativePath = Path.of(billNumber);
                    Path target = getAbsolutePath(targetDirRelativePath);
                    newAttachment.setRelativePath(targetDirRelativePath.resolve(origin.getFileName()).toString());
                    move(origin, target);
                }
                // 设置业务单据关联关系
                newAttachment = attachmentRepository.save(newAttachment);
                BillAttachRelation billAttachRelation = new BillAttachRelation();
                billAttachRelation.setBillId(billId);
                billAttachRelation.setBillType(billType);
                billAttachRelation.setAttachId(newAttachment.getId());
                billAttachRelationRepository.save(billAttachRelation);
            } else {
                // 未变化
                removeIds.remove(attachmentDTO.getId());
            }
        }
        // 删除
        for (BillAttachRelation billAttachRelation : billAttachRelations) {
            if (removeIds.contains(billAttachRelation.getAttachId())) {
                billAttachRelationRepository.delete(billAttachRelation);
            }
        }

        return attachmentRepository.findByBill(billId, billType);
    }

    /**
     * 删除没有业务关联的附件
     */
    private void cleanUnattach() {
        List<Attachment> attachments = attachmentRepository.findByBillIsNull();
        for (Attachment attachment : attachments) {
            delete(getAbsolutePath(Path.of(attachment.getRelativePath())));
            transactionTemplate.executeWithoutResult(status -> {
                attachmentRepository.delete(attachment);
            });
        }
    }

    /**
     * 清理文件
     */
    @PreDestroy
    public void cleanTemp() {
        // 删除临时文件
        delete(tempPath);
        // 删除没有业务关联的附件
        cleanUnattach();
    }
}
