package pers.eastwind.billmanager.servicebill.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.model.FileOp;
import pers.eastwind.billmanager.attach.model.FileOpType;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.attach.util.FileTxUtil;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.attach.util.OfficeFileUtil;
import pers.eastwind.billmanager.attach.util.SafePath;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.servicebill.model.ServiceBill;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillRepository;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceBillIOService {
    private final ServiceBillRepository serviceBillRepository;
    private final AttachService attachService;
    private final ServiceBillAttachService serviceBillAttachService;
    private final ServiceBillImportMapper attachMapper;

    public ServiceBillIOService(ServiceBillRepository serviceBillRepository, AttachService attachService, ServiceBillAttachService serviceBillAttachService, ServiceBillImportMapper attachMapper) {
        this.serviceBillRepository = serviceBillRepository;
        this.attachService = attachService;
        this.serviceBillAttachService = serviceBillAttachService;
        this.attachMapper = attachMapper;
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
        List<ServiceBill> serviceBills = serviceBillRepository.findAllById(ids);
        if (serviceBills.isEmpty()) {
            throw new BizException("id不存在");
        }
        Path tempPath = attachService.createTempDir("export");
        List<FileOp> ops = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("单据编号", "状态", "项目名称", "项目地址", "总额", "安装完成日期", "备注"));
        BigDecimal totalAmount = BigDecimal.ZERO;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (ServiceBill serviceBill : serviceBills) {
            rows.add(List.of(
                    serviceBill.getNumber(),
                    serviceBill.getState().getLabel(),
                    serviceBill.getProjectName(),
                    serviceBill.getProjectAddress(),
                    serviceBill.getTotalAmount().toString(),
                    serviceBill.getProcessedDate() == null ? "" : dateTimeFormatter.format(serviceBill.getProcessedDate().atZone(ZoneId.systemDefault())),
                    serviceBill.getDetails().stream().map((detail) ->
                                    detail.getDevice() + " : " + detail.getUnitPrice().stripTrailingZeros().toPlainString()
                                            + " * " + detail.getQuantity().stripTrailingZeros().toPlainString() + " ; ")
                            .collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(serviceBill.getTotalAmount());

            SafePath temp = SafePath.of(tempPath);
            SafePath curDir = temp.resolve(serviceBill.getNumber());

            List<Attach> attachments = serviceBillAttachService.findAttachments(serviceBill.getId());
            for (Attach attachment : attachments) {
                Path origin = attachService.getAbsolutePathByRela(attachment.getRelativePath());
                Path target = curDir.resolve(attachment.getName()).toPath();
                // 处理可能的重名
                int repeatCount = 1;
                while (Files.exists(target)) {
                    target = curDir.resolve(repeatCount + "-" + attachment.getName()).toPath();
                    repeatCount++;
                }
                ops.add(new FileOp(FileOpType.COPY, origin, target));
            }
        }
        rows.add(List.of("", "", "", "合计", totalAmount.toString(), ""));

        Path excel = SafePath.of(tempPath).resolve("导出结果.xlsx").toPath();
        OfficeFileUtil.generateExcelFromList(rows, excel);
        FileTxUtil.exec(ops);
        Path zip = SafePath.of(attachService.getTempPath()).resolve(tempPath + ".zip").toPath();
        FileUtil.zip(tempPath, zip);
        return zip;
    }
}
