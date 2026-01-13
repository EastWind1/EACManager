package pers.eastwind.billmanager.servicebill.service;

import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attachment;
import pers.eastwind.billmanager.attach.model.AttachmentMapper;
import pers.eastwind.billmanager.attach.model.AttachmentType;
import pers.eastwind.billmanager.attach.service.AttachMapService;
import pers.eastwind.billmanager.attach.service.AttachmentService;
import pers.eastwind.billmanager.attach.service.OCRService;
import pers.eastwind.billmanager.attach.service.OfficeFileService;
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

/**
 * 服务单导入导出
 */
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
@Service
public class ServiceBillIOService {
    private final ServiceBillRepository serviceBillRepository;
    private final AttachmentService attachmentService;
    private final AttachmentMapper attachmentMapper;
    private final OfficeFileService officeFileService;
    private final AttachMapService attachMapService;

    public ServiceBillIOService(ServiceBillRepository serviceBillRepository, AttachmentService attachmentService, AttachmentMapper attachmentMapper, OfficeFileService officeFileService, OCRService ocrService, AttachMapService attachMapService) {
        this.serviceBillRepository = serviceBillRepository;
        this.attachmentService = attachmentService;
        this.attachmentMapper = attachmentMapper;
        this.officeFileService = officeFileService;
        this.attachMapService = attachMapService;
    }

    /**
     * 根据文件生成单据
     *
     * @param resource 文件资源
     * @return 单据
     */
    public ServiceBillDTO generateByFile(Resource resource) {
        Attachment attachment = attachmentService.uploadTemp(List.of(resource)).getFirst();
        ServiceBillDTO bill = attachMapService.map(attachment);
        bill.setAttachments(List.of(attachmentMapper.toDTO(attachment)));
        return bill;
    }
    /**
     * 导出单据
     *
     * @param ids 单据列表
     * @return 压缩文件路径
     */
    public Path export(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("id不能为空");
        }
        List<ServiceBill> serviceBills = serviceBillRepository.findAllById(ids);
        if (serviceBills.isEmpty()) {
            throw new BizException("id不存在");
        }
        // 创建临时目录
        String dirName = "导出-" + System.currentTimeMillis();
        Path tempDir = attachmentService.getTempPath().resolve(dirName);
        tempDir = attachmentService.createDirectory(tempDir);
        // 遍历生成 excel行，并拷贝附件
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
                    dateTimeFormatter.format(serviceBill.getProcessedDate().atZone(ZoneId.systemDefault())),
                    serviceBill.getDetails().stream().map((detail) ->
                                    detail.getDevice() + " : " + detail.getUnitPrice().stripTrailingZeros().toPlainString()
                                            + " * " + detail.getQuantity().stripTrailingZeros().toPlainString() + " ; ")
                            .collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(serviceBill.getTotalAmount());
            // 复制附件文件夹
            Path origin = attachmentService.getAbsolutePath(Path.of(serviceBill.getNumber()));
            if (Files.exists(origin)) {
                attachmentService.copy(origin, tempDir, true);
            }
        }
        // 表合计
        rows.add(List.of("", "", "", "合计", totalAmount.toString(), ""));

        Path excel = tempDir.resolve("导出结果.xlsx");
        excel = attachmentService.createFile(excel);
        officeFileService.generateExcelFromList(rows, excel);
        Path zip = attachmentService.getTempPath().resolve(dirName + ".zip");
        attachmentService.zip(tempDir, zip);
        return zip;
    }
}
