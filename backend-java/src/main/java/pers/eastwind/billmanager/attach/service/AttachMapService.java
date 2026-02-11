package pers.eastwind.billmanager.attach.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.model.AttachmentType;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.attach.util.OfficeFileUtil;
import pers.eastwind.billmanager.common.exception.BizException;

import java.nio.file.Path;
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
     * @param attachment 附件
     * @param clazz 目标对象类
     * @return 对象
     */
    public <T> T map(AttachmentDTO attachment, Class<T> clazz) {
        Path path = attachmentService.getAbsolutePath(Path.of(attachment.getRelativePath()), attachment.isTemp());
        switch (attachment.getType()) {
            case IMAGE, PDF -> {
                if (attachment.getType() == AttachmentType.PDF) {
                    Path target = attachmentService.createTempFile("", ".jpg");
                    FileUtil.convertPDFToImage(path, target);
                    path = target;
                }
                List<String> texts = ocrService.parseImage(path);
                for (AttachMapRule<?> mapRule : mapRules) {
                    var cur = mapRule.mapFromOCR(texts);
                    if (cur != null) {
                        try {
                            return clazz.cast(cur);
                        } catch (ClassCastException _) {
                            log.error("{} OCR 类型转换失败", mapRule);
                        }
                    }
                }
                throw new BizException("未配置映射规则");
            }
            case EXCEL -> {
                List<List<String>> rows = OfficeFileUtil.parseExcel(path);
                for (AttachMapRule<?> mapRule : mapRules) {
                    var cur = mapRule.mapFromExcel(rows);
                    if (cur != null) {
                        try {
                            return clazz.cast(cur);
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
