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

    private boolean canOCR(List<String> texts) {
        for (String text : texts) {
            if (text.contains("菱电")) {
                return true;
            }
        }
        return false;
    }

    private boolean canExcel(List<List<String>> rows) {
        for (List<String> row : rows) {
            for (String text : row) {
                if (text.contains("菱电")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ServiceBillDTO mapFromTexts(List<String> texts) {
        if (!canOCR(texts)) {
            return null;
        }
        ServiceBillDTO serviceBill = super.mapFromTexts(texts);
        setCompany(serviceBill, "菱电");
        return serviceBill;
    }

    @Override
    public ServiceBillDTO mapFromGrid(List<List<String>> rows) {
        if (!canExcel(rows)) {
            return null;
        }
        ServiceBillDTO serviceBill = super.mapFromGrid(rows);
        setCompany(serviceBill, "菱电");
        return serviceBill;
    }
}
