package pers.eastwind.billmanager.reimburse.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.service.AttachContentService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.reimburse.model.ReimbursementDTO;

/**
 * 报销单附件映射
 * <p>仅支持标准税务发票（文本），规则由本模块自己持有
 */
@Service
public class ReimburseAttachMapper {
    private final AttachContentService contentService;
    private final ReimburseMapRule rule = new ReimburseMapRule();

    public ReimburseAttachMapper(AttachContentService contentService) {
        this.contentService = contentService;
    }

    /**
     * 附件映射为报销单
     *
     * @param attachment 附件
     * @return 报销单
     */
    public ReimbursementDTO map(AttachmentDTO attachment) {
        if (attachment == null) {
            throw new BizException("附件为空");
        }
        ReimbursementDTO dto = rule.mapFromTexts(contentService.text(attachment));
        if (dto == null) {
            throw new BizException("未配置映射规则");
        }
        return dto;
    }
}
