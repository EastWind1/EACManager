package pers.eastwind.billmanager.attach.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.model.AttachmentType;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.attach.util.OfficeFileUtil;
import pers.eastwind.billmanager.common.exception.BizException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 附件内容读取：图片/PDF 取文本块，Excel 取表格
 *
 */
@Slf4j
@Service
public class AttachContentService {
    private final OCRService ocrService;
    private final AttachmentService attachmentService;

    public AttachContentService(OCRService ocrService, AttachmentService attachmentService) {
        this.ocrService = ocrService;
        this.attachmentService = attachmentService;
    }

    /**
     * 读取文本块
     * <p>图片走 OCR；PDF 先取文本层，未识别到文本时转图片再 OCR
     *
     * @param attachment 附件
     * @return 文本块
     */
    public List<String> text(AttachmentDTO attachment) {
        Path path = attachmentService.getAbsolutePathById(attachment.getId());
        return switch (attachment.getType()) {
            case IMAGE -> ocrService.parseImage(path);
            case PDF -> {
                String[] texts = FileUtil.extractPDFText(path);
                if (texts.length > 0) {
                    yield Arrays.asList(texts);
                }
                Path temp = attachmentService.createTempFile("PDFToImage", "png");
                FileUtil.convertPDFToImage(path, temp);
                yield ocrService.parseImage(temp);
            }
            default -> throw new BizException("不支持解析为文本的附件类型");
        };
    }

    /**
     * 读取表格：仅 Excel
     *
     * @param attachment 附件
     * @return 表格内容
     */
    public List<List<String>> grid(AttachmentDTO attachment) {
        Path path = attachmentService.getAbsolutePathById(attachment.getId());
        if (attachment.getType() == AttachmentType.EXCEL) {
            return OfficeFileUtil.parseExcel(path);
        }
        throw new BizException("不支持解析为表格的附件类型");
    }

}
