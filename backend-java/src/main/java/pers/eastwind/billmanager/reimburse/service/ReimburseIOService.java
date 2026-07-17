package pers.eastwind.billmanager.reimburse.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.AttachmentDTO;
import pers.eastwind.billmanager.attach.model.BillType;
import pers.eastwind.billmanager.attach.model.FileOp;
import pers.eastwind.billmanager.attach.model.FileOpType;
import pers.eastwind.billmanager.attach.service.AttachMapService;
import pers.eastwind.billmanager.attach.service.AttachmentService;
import pers.eastwind.billmanager.attach.util.FileTxUtil;
import pers.eastwind.billmanager.attach.util.FileUtil;
import pers.eastwind.billmanager.attach.util.OfficeFileUtil;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.reimburse.model.Reimbursement;
import pers.eastwind.billmanager.reimburse.model.ReimbursementDTO;
import pers.eastwind.billmanager.reimburse.repository.ReimburseRepository;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报销单导入导出
 */
@Service
public class ReimburseIOService {
    private final AttachmentService attachmentService;
    private final AttachMapService attachMapService;
    private final ReimburseRepository reimburseRepository;

    public ReimburseIOService(AttachmentService attachmentService, AttachMapService attachMapService, ReimburseRepository reimburseRepository) {
        this.attachmentService = attachmentService;
        this.attachMapService = attachMapService;
        this.reimburseRepository = reimburseRepository;
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
        List<Reimbursement> reimbursements = reimburseRepository.findAllById(ids);
        if (reimbursements.isEmpty()) {
            throw new BizException("id不存在");
        }
        // 临时目录
        Path tempPath = attachmentService.createTempDir("export");
        // 文件操作
        List<FileOp> ops = new ArrayList<>();

        // 遍历生成 excel行，并拷贝附件
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("单据编号", "摘要", "总额", "报销日期", "备注"));
        BigDecimal totalAmount = BigDecimal.ZERO;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Reimbursement reimbursement : reimbursements) {
            rows.add(List.of(
                    reimbursement.getNumber(),
                    reimbursement.getSummary(),
                    reimbursement.getTotalAmount().toString(),
                    reimbursement.getReimburseDate() == null ? "" : dateTimeFormatter.format(reimbursement.getReimburseDate().atZone(ZoneId.systemDefault())),
                    reimbursement.getDetails().stream().map((detail) ->
                                    detail.getName() + " : " + detail.getAmount().stripTrailingZeros().toPlainString() + " ; ")
                            .collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(reimbursement.getTotalAmount());
            // 创建当前单据附件文件夹
            Path curDir = tempPath.resolve(reimbursement.getNumber());
            // 拷贝当前单据所有附件
            List<AttachmentDTO> attachments = attachmentService.getByBill(reimbursement.getId(), BillType.REIMBURSEMENT);
            for (AttachmentDTO attachment : attachments) {
                Path origin = attachmentService.getRootPath().resolve(attachment.getRelativePath());
                Path target = curDir.resolve(attachment.getName());
                // 处理可能的重名
                int repeatCount = 1;
                while (Files.exists(target)) {
                    target = curDir.resolve(repeatCount + "-" + attachment.getName());
                    repeatCount++;
                }
                ops.add(new FileOp(FileOpType.COPY, origin, target));
            }
        }
        // 表合计
        rows.add(List.of("", "合计", totalAmount.toString(), "", ""));
        // 获取当前时间字符串
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path excel = tempPath.resolve("导出结果" + timeStr + ".xlsx");
        OfficeFileUtil.generateExcelFromList(rows, excel);
        // 执行文件拷贝
        FileTxUtil.exec(ops);
        // 生成压缩包
        Path zip = attachmentService.getTempPath().resolve(tempPath + ".zip");
        FileUtil.zip(tempPath, zip);
        return zip;
    }
    /**
     * 根据文件生成报销单
     *
     * @param resource 文件资源
     * @return 报销单 DTO
     */
    public ReimbursementDTO generateByFile(org.springframework.core.io.Resource resource) {
        AttachmentDTO attachment = attachmentService.uploadTemps(List.of(resource)).getFirst();
        ReimbursementDTO dto = attachMapService.map(attachment);
        dto.setAttachments(List.of(attachment));
        return dto;
    }
}
