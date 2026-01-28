package pers.eastwind.billmanager.attach.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.model.AttachmentType;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.common.exception.BizException;

import java.nio.file.Path;
import java.util.List;

/**
 * 附件映射服务
 */
@Service
public class AttachMapService {
    private final OCRService ocrService;
    private final OfficeFileService officeFileService;
    private final List<AttachMapRule<?>> mapRules;
    private final AttachmentService attachmentService;

    public AttachMapService(OCRService ocrService, OfficeFileService officeFileService, List<AttachMapRule<?>> mapRules, AttachmentService attachmentService) {
        this.ocrService = ocrService;
        this.officeFileService = officeFileService;
        this.mapRules = mapRules;
        this.attachmentService = attachmentService;
    }

    /**
     * 附件映射为对象
     * @param attachment 附件
     * @return 对象
     */
    @SuppressWarnings("unchecked")
    public <T> T map(AttachmentDTO attachment) {
        Path path = attachmentService.getAbsolutePath(Path.of(attachment.getRelativePath()));
        switch (attachment.getType()) {
            case IMAGE, PDF -> {
                if (attachment.getType() == AttachmentType.PDF) {
                    Path target = attachmentService.getTempPath().resolve(System.currentTimeMillis()+".jpg");
                    FileUtil.convertPDFToImage(path, target);
                    path = target;
                }
                List<String> texts = ocrService.parseImage(path);
                for (AttachMapRule<?> mapRule : mapRules) {
                    if (mapRule.canOCR(texts)) {
                        return (T) mapRule.mapFromOCR(texts);
                    }
                }
                throw new BizException("未配置映射规则");
            }
            case EXCEL -> {
                List<List<String>> rows = officeFileService.parseExcel(path);
                for (AttachMapRule<?> mapRule : mapRules) {
                    if (mapRule.canExcel(rows)) {
                        return (T) mapRule.mapFromExcel(rows);
                    }
                }
                throw new BizException("未配置映射规则");
            }
            default -> throw new BizException("不支持该附件类型映射");
        }
    }
}
