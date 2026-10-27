package pers.eastwind.billmanager.servicebill.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.model.AttachType;
import pers.eastwind.billmanager.attach.service.AttachContentService;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.company.service.CompanyService;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;

import java.util.List;

/**
 * 服务单附件映射：解析规则由本模块自己维护。
 */
@Service
public class ServiceBillImportMapper {
    private final AttachContentService contentService;
    private final List<ServiceBillImportRule> rules;

    public ServiceBillImportMapper(AttachContentService contentService, CompanyService companyService) {
        this.contentService = contentService;
        this.rules = List.of(
                new ServiceBillImportRule(companyService, "威垦"),
                new ServiceBillImportRule(companyService, "菱电")
        );
    }

    public ServiceBillDTO map(AttachDTO attachment) {
        if (attachment == null) {
            throw new BizException("附件为空");
        }
        if (attachment.getType() == AttachType.EXCEL) {
            return fromGrid(contentService.grid(attachment));
        }
        return fromTexts(contentService.text(attachment));
    }

    private ServiceBillDTO fromTexts(List<String> texts) {
        for (ServiceBillImportRule rule : rules) {
            ServiceBillDTO dto = rule.mapFromTexts(texts);
            if (dto != null) {
                return dto;
            }
        }
        throw new BizException("未配置映射规则");
    }

    private ServiceBillDTO fromGrid(List<List<String>> rows) {
        for (ServiceBillImportRule rule : rules) {
            ServiceBillDTO dto = rule.mapFromGrid(rows);
            if (dto != null) {
                return dto;
            }
        }
        throw new BizException("未配置映射规则");
    }
}
