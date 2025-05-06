package com.eastwind.EACAfterSaleMgr.model.dto;

import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBillState;
import com.eastwind.EACAfterSaleMgr.model.entity.ServiceBillType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务单 DTO
 */
@Data
public class ServiceBillDTO {
    private Integer id;
    /**
     * 单号
     */
    private String number;
    /**
     * 单据类型
     */
    private ServiceBillType type;
    /**
     * 单据状态
     */
    private ServiceBillState state;
    /**
     * 项目名称
     */
    private String projectName;
    /**
     * 项目地址
     */
    private String projectAddress;
    /**
     * 项目联系人
     */
    private String projectContact;
    /**
     * 项目联系人电话
     */
    private String projectContactPhone;
    /**
     * 现场联系人
     */
    private String onSiteContact;
    /**
     * 现场联系人电话
     */
    private String onSitePhone;
    /**
     * 电梯信息
     */
    private String elevatorInfo;
    /**
     * 处理人明细列表
     */
    private List<ServiceBillProcessorDetailDTO> processDetails;
    /**
     * 服务明细
     */
    private List<ServiceBillDetailDTO> details;
    /**
     * 附件列表
     */
    private List<AttachmentDTO> attachments;
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    /**
     * 完工时间
     */
    private LocalDateTime processedDate;
    /**
     * 备注
     */
    private String remark;
    /**
     * 创建时间
     */
    private LocalDateTime createdDate;
    /**
     * 最后修改时间
     */
    private LocalDateTime lastModifiedDate;
}
