package pers.eastwind.billmanager.attach.util;

import org.apache.commons.io.file.PathUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import pers.eastwind.billmanager.attach.model.AttachmentType;
import pers.eastwind.billmanager.attach.model.UploadResult;
import pers.eastwind.billmanager.common.exception.BizException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件工具类
 */
public class FileUtil {
    /**
     * 创建目录
     * @param path 文件夹路径
     */
    public static void createDirectories(Path path) {
        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) {
                throw new BizException("创建目录失败，目标存在且为文件");
            }
        } else {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new BizException("创建目录失败", e);
            }
        }
    }
    /**
     * 拷贝
     * @param origin 源文件路径
     */
    public static void copy(Path origin, Path target) {
        if (!Files.exists(origin) || Files.isDirectory(origin)) {
            throw new BizException("拷贝源必须存在且为文件");
        }
        if (Files.exists(target)) {
            throw new BizException("拷贝目标存在");
        }
        try {
            Files.copy(origin, target);
        } catch (IOException e) {
            throw new BizException("拷贝失败", e);
        }
    }
    /**
     * 获取文件类型
     *
     * @param path 文件路径
     * @return 文件类型
     */
    private static AttachmentType getFileType(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
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
     * 提取 PDF 为图片
     * 暂时只支持转换第一页
     */
    public static void convertPDFToImage(Path source, Path target) {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() <= 0) {
                throw new IllegalArgumentException("PDF 文件为空");
            }
            Path imagePath = target.resolve("PDFImage-" + System.currentTimeMillis() + ".png");;
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImage(0);
            ImageIO.write(image, "png", imagePath.toFile());
        } catch (IOException e) {
            throw new BizException("提取 PDF 图片失败", e);
        }
    }

    /**
     * 上传单个文件
     *
     * @param resource 文件资源
     * @param path     目录路径
     * @return 上传结果
     */
    public static UploadResult upload(Resource resource, Path path) {
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }

        String fileName = resource.getFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        AttachmentType type;
        try {
            PathUtils.createParentDirectories(path);
            Files.copy(resource.getInputStream(), path);
            // 文件类型判断只能在保存文件之后
            type = getFileType(path);
        } catch (Exception e) {
            throw new BizException("上传文件失败", e);
        }

        return new UploadResult(fileName, type, path);
    }

    /**
     * 读取文件
     *
     * @param path 文件路径
     * @return 文件资源
     */
    public static Resource loadByPath(Path path) {
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new BizException("文件不存在");
        }
        return new FileSystemResource(path);
    }

    /**
     * 压缩文件或文件夹
     *
     * @param sourcePath 源路径
     * @param targetPath 目标文件路径
     */
    public static void zip(Path sourcePath, Path targetPath) {
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(targetPath.toFile()))) {
            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> stream = Files.walk(sourcePath)) {
                    stream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                        String name = sourcePath.relativize(path).toString().replace("\\", "/");
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
                String name = sourcePath.getFileName().toString();
                ZipEntry zipEntry = new ZipEntry(name);
                zipOut.putNextEntry(zipEntry);
                Files.copy(sourcePath, zipOut);
                zipOut.closeEntry();
            }
        } catch (IOException e) {
            throw new BizException("压缩文件失败", e);
        }
    }
}
