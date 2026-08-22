package pers.eastwind.billmanager.attach.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.attach.util.OfficeFileUtil;
import pers.eastwind.billmanager.common.exception.BizException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 附件映射服务
 */
@Slf4j
@Service
public class AttachMapService {
    private final OCRService ocrService;
    private final List<AttachMapRule<?>> mapRules;
    private final AttachmentService attachmentService;

    public AttachMapService(OCRService ocrService, List<AttachMapRule<?>> mapRules, AttachmentService attachmentService) {
        this.ocrService = ocrService;
        this.mapRules = mapRules;
        this.attachmentService = attachmentService;
    }

    /**
     * 附件映射为对象
     *
     * @param attachment 附件
     * @return 对象
     */
    @SuppressWarnings("unchecked")
    public <T> T map(AttachmentDTO attachment) {
        Path path = attachmentService.getAbsolutePathById(attachment.getId());
        switch (attachment.getType()) {
            case IMAGE -> {
                List<String> texts = ocrService.parseImage(path);
                for (AttachMapRule<?> mapRule : mapRules) {
                    var cur = mapRule.mapFromTexts(texts);
                    if (cur != null) {
                        try {
                            return (T) cur;
                        } catch (ClassCastException _) {
                            log.error("{} IMAGE 类型转换失败", mapRule);
                        }
                    }
                }
                throw new BizException("未配置映射规则");
            }
            case PDF -> {
                List<String> texts;
                // 先尝试直接解析文本, 没有文本时再调用 OCR 服务
                String[] text = FileUtil.extractPDFText(path);
                if (text.length > 0) {
                    texts = Arrays.asList(text);
                } else {
                    Path temp = attachmentService.createTempFile("PDFToImage", "png");
                    FileUtil.convertPDFToImage(path, temp);
                    texts = ocrService.parseImage(temp);
                }

                for (AttachMapRule<?> mapRule : mapRules) {
                    var cur = mapRule.mapFromTexts(texts);
                    if (cur != null) {
                        try {
                            return (T) cur;
                        } catch (ClassCastException _) {
                            log.error("{} PDF 类型转换失败", mapRule);
                        }
                    }
                }
                throw new BizException("未配置映射规则");
            }
            case EXCEL -> {
                List<List<String>> rows = OfficeFileUtil.parseExcel(path);
                for (AttachMapRule<?> mapRule : mapRules) {
                    var cur = mapRule.mapFromGrid(rows);
                    if (cur != null) {
                        try {
                            return (T) cur;
                        } catch (ClassCastException _) {
                            log.error("{} Excel 类型转换失败", mapRule);
                        }
                    }
                }
                throw new BizException("未配置映射规则");
            }
            default -> throw new BizException("不支持该附件类型映射");
        }
    }
}
