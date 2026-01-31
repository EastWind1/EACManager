package pers.eastwind.billmanager.servicebill.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.service.AttachMapRule;
import pers.eastwind.billmanager.company.model.CompanyDTO;
import pers.eastwind.billmanager.company.service.CompanyService;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;


/**
 * 威垦服务单映射
 */
@Service
public class WKServiceBillAttachMapRule implements AttachMapRule<ServiceBillDTO> {
    private final CompanyService companyService;
    public WKServiceBillAttachMapRule(CompanyService companyService) {
        this.companyService = companyService;
    }

    private final Map<String, BiConsumer<ServiceBillDTO, String>> mapRules = Map.of(
            "合同编号", ServiceBillDTO::setNumber,
            "下单时间", ( target,  text) -> target.setOrderDate(AttachMapRule.parseDateString(text)),
            "项目名称", ServiceBillDTO::setProjectName,
            "监理、站长", ServiceBillDTO::setProjectContact,
            "现场联系人", ServiceBillDTO::setOnSiteContact,
            "项目地址", ServiceBillDTO::setProjectAddress,
            "备注", ServiceBillDTO::setRemark
    );



    protected void setByText(ServiceBillDTO target, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] labels = text.split("：");
        if (labels.length < 2) {
            // 处理半角
            labels = text.split(":");
            if (labels.length < 2) {
                return;
            }
        }
        if (!mapRules.containsKey(labels[0])) {
            return;
        }
        mapRules.get(labels[0]).accept(target, labels[1]);
    }

    private boolean canOCR(List<String> texts) {
        for (String text : texts) {
            if (text.contains("威垦")) {
                return true;
            }
        }
        return false;
    }

    private boolean canExcel(List<List<String>> rows) {
        for (List<String> row : rows) {
            for (String text : row) {
                if (text.contains("威垦")) {
                    return true;
                }
            }
        }
        return false;
    }
    protected void setCompany(ServiceBillDTO target, String name) {
        List<CompanyDTO> company = companyService.findByName(name);
        if (!company.isEmpty()) {
            target.setProductCompany(company.getFirst());
        }
    }
    @Override
    public ServiceBillDTO mapFromOCR(List<String> texts) {
        if (!canOCR(texts)) {
            return null;
        }
        ServiceBillDTO serviceBill = new ServiceBillDTO();
        setCompany(serviceBill, "威垦");
        for (String text : texts) {
            setByText(serviceBill, text);
        }
        // OCR 实现子表映射过于复杂，暂不处理
        return serviceBill;
    }

    @Override
    public ServiceBillDTO mapFromExcel(List<List<String>> rows) {
        if (!canExcel(rows)) {
            return null;
        }
        ServiceBillDTO serviceBill = new ServiceBillDTO();
        setCompany(serviceBill, "威垦");
        serviceBill.setDetails(new ArrayList<>());
        // 明细开始索引行
        int detailStartIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            // 明细开始
            if (row.getFirst().contains("序号")) {
                detailStartIndex = i + 1;
                continue; // 跳过表头
            }
            // 明细结束
            if (row.getFirst().contains("出货信息")) {
                detailStartIndex = -1;
            }
            // 主表
            if (detailStartIndex == -1) {
                for (String text : row) {
                    setByText(serviceBill, text);
                }
            } else {
                // 普通列表项, 首个个单元格为数字
                // 由于源文件是预制列表行，然后把不需要的行设为白色，要单独处理数据无效行
                if (row.get(0).matches("^\\d+\\.?\\d+$")) {
                    ServiceBillDetailDTO detail = new ServiceBillDetailDTO();
                    detail.setDevice(row.get(1) + " " + row.get(2) + " " + row.get(4));
                    if (row.get(5).isEmpty() || row.get(7).isEmpty() || row.get(8).isEmpty()) {
                        continue;
                    }
                    detail.setQuantity(new BigDecimal(row.get(5)));
                    detail.setUnitPrice(new BigDecimal(row.get(7)));
                    detail.setSubtotal(new BigDecimal(row.get(8)));
                  
                    serviceBill.getDetails().add(detail);
                } else {
                    // 特殊子项
                    for (int j = 0; j < row.size(); j++) {
                        String text = row.get(j);
                        if (text.contains("路") && j + 2 < row.size() && !row.get(j + 2).isEmpty()) {
                            ServiceBillDetailDTO detail = new ServiceBillDetailDTO();
                            detail.setDevice("路费补贴");
                            detail.setQuantity(BigDecimal.ONE);
                            detail.setUnitPrice(new BigDecimal(row.get(j + 2)));
                            detail.setSubtotal(new BigDecimal(row.get(j + 2)));
                            serviceBill.getDetails().add(detail);
                        }
                        if (text.contains("合计") && j + 2 < row.size()) {
                            serviceBill.setTotalAmount(new BigDecimal(row.get(j + 2)));
                        }
                    }
                }
            }
        }
        return serviceBill;
    }
}
