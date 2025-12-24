package pers.eastwind.billmanager.servicebill.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.service.AttachMapRule;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 威垦服务单映射
 */
@Service
public class WKServiceBillAttachMapRule implements AttachMapRule<ServiceBillDTO> {

    private final Map<String, String> mapRules = Map.of(
            "合同编号", "number",
            "下单时间", "orderDate",
            "项目名称", "projectName",
            "监理、站长", "projectContact",
            "现场联系人", "onSiteContact",
            "项目地址", "projectAddress",
            "备注", "remark"
    );

    private void setByText(ServiceBillDTO target, String text) {
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
        AttachMapRule.setStringValue(target, mapRules.get(labels[0]), labels[1]);
    }

    @Override
    public boolean canOCR(List<String> texts) {
        for (String text : texts) {
            // 暂不支持维修单
            if (text.contains("维修")) {
                return false;
            }
            if (text.contains("威垦")) {
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
                if (text.contains("威垦")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ServiceBillDTO mapFromOCR(List<String> texts) {
        ServiceBillDTO serviceBill = new ServiceBillDTO();
        for (String text : texts) {
            setByText(serviceBill, text);
        }
        return serviceBill;
    }

    @Override
    public ServiceBillDTO mapFromExcel(List<List<String>> rows) {
        ServiceBillDTO serviceBill = new ServiceBillDTO();
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
                    if (!row.get(5).isEmpty()) {
                        detail.setQuantity(new BigDecimal(row.get(5)));
                    } else {
                        continue;
                    }
                    if (!row.get(7).isEmpty()) {
                        detail.setUnitPrice(new BigDecimal(row.get(7)));
                    } else {
                        continue;
                    }
                    if (!row.get(8).isEmpty()) {
                        detail.setSubtotal(new BigDecimal(row.get(8)));
                    } else {
                        continue;
                    }
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
