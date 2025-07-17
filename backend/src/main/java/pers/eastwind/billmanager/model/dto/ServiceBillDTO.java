package pers.eastwind.billmanager.model.dto;

import lombok.Data;
import pers.eastwind.billmanager.model.common.ServiceBillState;
import pers.eastwind.billmanager.model.common.ServiceBillType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private ServiceBillType type = ServiceBillType.INSTALL;
    /**
     * 单据状态
     */
    private ServiceBillState state = ServiceBillState.CREATED;
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
     * 服务明细
     */
    private List<ServiceBillDetailDTO> details = new ArrayList<>();
    /**
     * 附件列表
     */
    private List<AttachmentDTO> attachments = new ArrayList<>();
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    /**
     * 下单时间
     */
    private Instant orderDate = Instant.now();
    /**
     * 完工时间
     */
    private Instant processedDate;
    /**
     * 备注
     */
    private String remark;
}
