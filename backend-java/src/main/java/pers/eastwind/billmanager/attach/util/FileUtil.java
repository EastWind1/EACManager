package pers.eastwind.billmanager.attach.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.Resource;
import pers.eastwind.billmanager.attach.model.AttachmentType;
import pers.eastwind.billmanager.common.exception.FileOpException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件工具类
 */
@Slf4j
public class FileUtil {
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
                    throw new FileOpException("不支持的文件类型: " + mimeType);
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
            throw new FileOpException("提取 PDF 图片失败", e);
        }
    }
    /**
     * 上传结果
     * @param filename 文件名
     * @param type     类型
     * @param path     相对路径
     */
    public record UploadResult(String filename, AttachmentType type, Path path) {
    }

    /**
     * 上传单个文件
     *
     * @param resource 文件资源
     * @param tempDirPath 临时目录路径, 为空时使用系统临时目录
     * @return 上传结果
     */
    public static UploadResult upload(Resource resource, Path tempDirPath) {
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("禁止上传空文件");
        }

        String fileName = resource.getFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (tempDirPath == null) {
            tempDirPath = PathUtils.getTempDirectory();
        }
        AttachmentType type;
        Path target = null;
        try {
            target = Files.createTempFile(tempDirPath, "", "-" + fileName);
            target.toFile().deleteOnExit();
            Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            // 文件类型判断只能在保存文件之后
            type = getFileType(target);
        } catch (Exception e) {
            try {
                if (target != null) {
                    Files.deleteIfExists(target);
                }
            } catch (IOException ei) {
                log.error("删除临时文件失败", ei);
            }
            throw new FileOpException("上传文件失败", e);
        }

        return new UploadResult(fileName, type, target);
    }

    /**
     * 压缩文件或文件夹
     *
     * @param source 源路径
     * @param target 目标文件，为空则自动生成
     * @return 压缩后的文件路径
     */
    public static Path zip(Path source, Path target) {
        if (source == null ||!Files.exists(source)) {
            throw new FileOpException("源文件不存在");
        }
        if (target == null) {
            try {
                target = Files.createTempFile(null, ".zip");
            } catch (IOException e) {
                throw new FileOpException(e);
            }
        }
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(target.toFile()))) {
            if (Files.isDirectory(source)) {
                try (Stream<Path> stream = Files.walk(source)) {
                    stream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                        String name = source.relativize(path).toString().replace("\\", "/");
                        ZipEntry zipEntry = new ZipEntry(name);
                        try {
                            zipOut.putNextEntry(zipEntry);
                            Files.copy(path, zipOut);
                            zipOut.closeEntry();
                        } catch (IOException e) {
                            throw new FileOpException("压缩文件失败", e);
                        }
                    });
                }
            } else {
                String name = source.getFileName().toString();
                ZipEntry zipEntry = new ZipEntry(name);
                zipOut.putNextEntry(zipEntry);
                Files.copy(source, zipOut);
                zipOut.closeEntry();
            }
        } catch (IOException e) {
            throw new FileOpException("压缩文件失败", e);
        }
        return target;
    }
}
