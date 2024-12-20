package com.eastwind.ElevatorACAfterSaleManager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 服务单据实体类
 */
@Entity
@Data
@EqualsAndHashCode(callSuper=true)
public class ServiceBillEntity extends AuditEntity {
    @Id
    @GeneratedValue
    private int id;
    /**
     * 单号
     */
    private String number;
    /**
     * 单据类型
     */
    @Enumerated
    private ServiceBillType type;
    /**
     * 单据状态
     */
    @Enumerated
    private String state;
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
    private String projectContactor;
    /**
     * 项目联系人电话
     */
    private String projectContactPhone;
    /**
     * 电梯信息
     */
    private String elevatorInfo;
    /**
     * 处理人明细列表
     */
    @OneToMany
    private List<ServiceBillProcessorDetail> processDetails;

    /**
     * 总金额
     */
    @Column(scale = 3)
    private BigDecimal totalAmount;
    /**
     * 完工时间
     */
    private OffsetDateTime processedDate;

}

