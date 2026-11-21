package pers.eastwind.billmanager.servicebill.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.attach.util.OfficeFileUtil;
import pers.eastwind.billmanager.attach.util.SafePath;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.servicebill.model.ServiceBill;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 服务单导入导出服务
 */
@Service
public class ServiceBillIOService {
    private final AttachService attachService;
    private final ServiceBillAttachService serviceBillAttachService;
    private final ServiceBillImportMapper attachMapper;
    private final ServiceBillQueryService queryService;

    public ServiceBillIOService(AttachService attachService, ServiceBillAttachService serviceBillAttachService, ServiceBillImportMapper attachMapper, ServiceBillQueryService queryService) {
        this.attachService = attachService;
        this.serviceBillAttachService = serviceBillAttachService;
        this.attachMapper = attachMapper;
        this.queryService = queryService;
    }

    public ServiceBillDTO generateByFile(Resource resource) {
        AttachDTO attachment = attachService.upload(ServiceBillAttachService.ATTACH_NAMESPACE, List.of(resource)).getFirst();
        ServiceBillDTO bill = attachMapper.map(attachment);
        bill.setAttachments(List.of(attachment));
        return bill;
    }

    public Path export(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        List<ServiceBill> serviceBills = queryService.findEntityByIds(ids);
        Path tempPath = attachService.createTempDir("export");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("单据编号", "状态", "项目名称", "项目地址", "总额", "安装完成日期", "备注"));
        BigDecimal totalAmount = BigDecimal.ZERO;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (ServiceBill serviceBill : serviceBills) {
            rows.add(List.of(
                    text(serviceBill.getNumber()),
                    serviceBill.getState() == null ? "" : serviceBill.getState().getLabel(),
                    text(serviceBill.getProjectName()),
                    text(serviceBill.getProjectAddress()),
                    amountText(serviceBill.getTotalAmount()),
                    serviceBill.getProcessedDate() == null ? ""
                            : dateTimeFormatter.format(serviceBill.getProcessedDate().atZone(ZoneId.systemDefault())),
                    serviceBill.getDetails().stream().map((detail) ->
                                    text(detail.getDevice()) + " : " + amountText(detail.getUnitPrice())
                                            + " * " + amountText(detail.getQuantity()) + " ; ")
                            .collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(serviceBill.getTotalAmount());

            SafePath temp = SafePath.of(tempPath);
            SafePath curDir = temp.resolve(serviceBill.getNumber());

            List<Attach> attachments = serviceBillAttachService.findAttachments(serviceBill.getId());
            attachService.copyTo(
                    attachments.stream().map(Attach::getId).toList(),
                    curDir.toPath());
        }
        rows.add(List.of("", "", "", "合计", totalAmount.toString(), ""));

        Path excel = SafePath.of(tempPath).resolve("导出结果.xlsx").toPath();
        OfficeFileUtil.generateExcelFromList(rows, excel);
        return attachService.zipDirectory(tempPath);
    }

    /** 导出的单元格不接受 null：List.of 遇到 null 会直接抛 NPE。 */
    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String amountText(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
