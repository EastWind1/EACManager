package com.eastwind.EACAfterSaleMgr.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 服务单据实体类
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceBill extends AuditEntity {
    @Id
    @GeneratedValue
    private Integer id;
    /**
     * 单号
     */
    private String number;
    /**
     * 单据类型
     */
    @Enumerated
    private ServiceBillType type = ServiceBillType.INSTALL;
    /**
     * 单据状态
     */
    @Enumerated
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
     * 总金额
     */
    @Column(scale = 3)
    private BigDecimal totalAmount;
    /**
     * 完工时间
     */
    private ZonedDateTime processedDate;
    /**
     * 备注
     */
    @Column(length = 1000)
    private String remark;
    /**
     * 处理人明细列表
     */
    @OneToMany
    @JoinColumn(name = "service_bill_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private List<ServiceBillProcessorDetail> processDetails;
    /**
     * 服务明细
     */
    @OneToMany
    @JoinColumn(name = "service_bill_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private List<ServiceBillDetail> details;

}

