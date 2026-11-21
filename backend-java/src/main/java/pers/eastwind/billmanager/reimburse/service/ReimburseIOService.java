package pers.eastwind.billmanager.reimburse.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.model.Attach;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.attach.service.AttachService;
import pers.eastwind.billmanager.attach.util.OfficeFileUtil;
import pers.eastwind.billmanager.attach.util.SafePath;
import pers.eastwind.billmanager.reimburse.model.Reimburse;
import pers.eastwind.billmanager.reimburse.model.ReimburseDTO;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReimburseIOService {
    private final AttachService attachService;
    private final ReimburseAttachService reimburseAttachService;
    private final ReimburseImportMapper attachMapper;
    private final ReimburseQueryService queryService;

    public ReimburseIOService(AttachService attachService, ReimburseAttachService reimburseAttachService, ReimburseImportMapper attachMapper, ReimburseQueryService queryService) {
        this.attachService = attachService;
        this.reimburseAttachService = reimburseAttachService;
        this.attachMapper = attachMapper;
        this.queryService = queryService;
    }

    public Path export(List<Integer> ids) {
        List<Reimburse> reimbursements = queryService.findEntityByIds(ids);

        Path tempPath = attachService.createTempDir("export");

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("单据编号", "摘要", "总额", "报销日期", "备注"));
        BigDecimal totalAmount = BigDecimal.ZERO;
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Reimburse reimbursement : reimbursements) {
            rows.add(List.of(
                    text(reimbursement.getNumber()),
                    text(reimbursement.getSummary()),
                    amountText(reimbursement.getTotalAmount()),
                    reimbursement.getReimburseDate() == null ? "" : dateTimeFormatter.format(reimbursement.getReimburseDate().atZone(ZoneId.systemDefault())),
                    reimbursement.getDetails().stream().map((detail) ->
                                    text(detail.getName()) + " : " + amountText(detail.getAmount()) + " ; ")
                            .collect(Collectors.joining())
            ));
            totalAmount = totalAmount.add(reimbursement.getTotalAmount());
            SafePath temp = SafePath.of(tempPath);
            SafePath curDir = temp.resolve(reimbursement.getNumber());
            List<Attach> attachments = reimburseAttachService.findAttachments(reimbursement.getId());
            attachService.copyTo(
                    attachments.stream().map(Attach::getId).toList(),
                    curDir.toPath());
        }
        rows.add(List.of("", "合计", totalAmount.toString(), "", ""));

        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path excel = SafePath.of(tempPath).resolve("导出结果" + timeStr + ".xlsx").toPath();
        OfficeFileUtil.generateExcelFromList(rows, excel);
        return attachService.zipDirectory(tempPath);
    }

    public ReimburseDTO generateByFile(Resource resource) {
        AttachDTO attachment = attachService.upload(ReimburseAttachService.ATTACH_NAMESPACE, List.of(resource)).getFirst();
        ReimburseDTO dto = attachMapper.map(attachment);
        dto.setAttachments(List.of(attachment));
        return dto;
    }

    /** 导出的单元格不接受 null：List.of 遇到 null 会直接抛 NPE。 */
    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String amountText(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
