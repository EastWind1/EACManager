package pers.eastwind.billmanager.attach.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import pers.eastwind.billmanager.attach.model.AttachType;
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

@Slf4j
public class FileUtil {
    private static AttachType getFileType(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
        if (mimeType == null) {
            return AttachType.OTHER;
        }
        return switch (mimeType) {
            case "application/pdf" -> AttachType.PDF;
            case "image/jpeg", "image/png", "image/gif" -> AttachType.IMAGE;
            case "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    AttachType.WORD;
            case "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    AttachType.EXCEL;
            // 禁止可执行文件
            case "application/x-msdownload", "application/x-executable", "application/x-sh", "application/x-bat" ->
                    throw new FileOpException("不支持的文件类型: " + mimeType);
            default -> AttachType.OTHER;
        };
    }

    /**
     * 只转换 PDF 的第一页。
     */
    public static void convertPDFToImage(Path source, Path target) {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() <= 0) {
                throw new IllegalArgumentException("PDF 文件为空");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImage(0);
            ImageIO.write(image, "png", target.toFile());
        } catch (IOException e) {
            throw new FileOpException("PDF 转图片失败", e);
        }
    }

    public static String[] extractPDFText(Path source) {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            if (document.getNumberOfPages() <= 0) {
                throw new IllegalArgumentException("PDF 文件为空");
            }

            var extractor = new PDFTextStripper();
            String text = extractor.getText(document);
            if (text.isBlank()) {
                return new String[0];
            }
            return text.split("\r?\n");
        } catch (IOException e) {
            throw new FileOpException("PDF 文本提取失败", e);
        }
    }

    /**
     * 写入上传文件并返回按内容判定的类型。
     */
    public static AttachType save(Resource resource, Path target) {
        if (resource == null || !resource.exists()) {
            throw new FileOpException("禁止上传空文件");
        }
        try {
            Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return getFileType(target);
        } catch (Exception e) {
            deleteQuietly(target);
            throw new FileOpException("上传文件失败", e);
        }
    }

    public static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("删除附件文件失败: {}", path, e);
        }
    }

    public static Path zip(Path source, Path target) {
        if (source == null || !Files.exists(source)) {
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
