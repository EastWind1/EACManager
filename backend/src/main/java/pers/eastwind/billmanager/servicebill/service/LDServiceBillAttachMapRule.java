package pers.eastwind.billmanager.servicebill.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.company.service.CompanyService;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;

import java.util.List;


/**
 * 菱电服务单映射
 * 两家公司实际为统一制造商供货，逻辑相同
 */
@Service
public class LDServiceBillAttachMapRule extends WKServiceBillAttachMapRule {

    public LDServiceBillAttachMapRule(CompanyService companyService) {
        super(companyService);
    }

    @Override
    public boolean canOCR(List<String> texts) {
        for (String text : texts) {
            // 暂不支持维修单
            if (text.contains("维修")) {
                return false;
            }
            if (text.contains("菱电")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canExcel(List<List<String>> rows) {
        for (List<String> row : rows) {
            for (String text : row) {
                // 暂不支持维修单
                if (text.contains("维修")) {
                    return false;
                }
                if (text.contains("菱电")) {
                    return true;
                }
            }
        }
        return false;
    }
    @Override
    public ServiceBillDTO mapFromOCR(List<String> texts) {
        ServiceBillDTO serviceBill = super.mapFromOCR(texts);
        setCompany(serviceBill, "菱电");
        return serviceBill;
    }

    @Override
    public ServiceBillDTO mapFromExcel(List<List<String>> rows) {
        ServiceBillDTO serviceBill = super.mapFromExcel(rows);
        setCompany(serviceBill, "菱电");
        return serviceBill;
    }
}
