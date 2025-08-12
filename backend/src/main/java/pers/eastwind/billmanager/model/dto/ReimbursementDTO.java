package pers.eastwind.billmanager.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 报销单 DTO
 */
@Data
public class ReimbursementDTO {
    private Integer id;
    /**
     * 编号
     */
    private String number;
    /**
     * 摘要
     */
    private String summary;
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    /**
     * 报销日期
     */
    private Instant reimburseDate;
    /**
     * 备注
     */
    private String remark;

    /**
     * 明细
     */
    private List<ReimburseDetailDTO> details = new ArrayList<>();

    /**
     * 附件
     */
    private List<AttachmentDTO> attachments = new ArrayList<>();
}
