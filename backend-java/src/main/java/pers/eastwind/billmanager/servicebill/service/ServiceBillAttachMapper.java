package pers.eastwind.billmanager.servicebill.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.model.AttachmentType;
import pers.eastwind.billmanager.attach.service.AttachContentService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.company.service.CompanyService;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;

import java.util.List;

/**
 * 服务单附件映射
 * <p>规则集合由本模块自己维护：菱电判定更具体，排在威垦之前
 */
@Service
public class ServiceBillAttachMapper {
    private final AttachContentService contentService;
    private final List<ServiceBillAttachMapRule> rules;

    public ServiceBillAttachMapper(AttachContentService contentService, CompanyService companyService) {
        this.contentService = contentService;
        this.rules = List.of(
                new ServiceBillAttachMapRule(companyService, "威垦"),
                new ServiceBillAttachMapRule(companyService, "菱电")
        );
    }

    /**
     * 附件映射为服务单
     *
     * @param attachment 附件
     * @return 服务单
     */
    public ServiceBillDTO map(AttachmentDTO attachment) {
        if (attachment == null) {
            throw new BizException("附件为空");
        }
        if (attachment.getType() == AttachmentType.EXCEL) {
            return fromGrid(contentService.grid(attachment));
        }
        return fromTexts(contentService.text(attachment));
    }

    private ServiceBillDTO fromTexts(List<String> texts) {
        for (ServiceBillAttachMapRule rule : rules) {
            ServiceBillDTO dto = rule.mapFromTexts(texts);
            if (dto != null) {
                return dto;
            }
        }
        throw new BizException("未配置映射规则");
    }

    private ServiceBillDTO fromGrid(List<List<String>> rows) {
        for (ServiceBillAttachMapRule rule : rules) {
            ServiceBillDTO dto = rule.mapFromGrid(rows);
            if (dto != null) {
                return dto;
            }
        }
        throw new BizException("未配置映射规则");
    }
}
