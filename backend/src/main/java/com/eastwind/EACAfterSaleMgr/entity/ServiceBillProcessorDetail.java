package com.eastwind.EACAfterSaleMgr.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 服务单处理人明细
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceBillProcessorDetail extends AuditEntity {

    @Id
    @GeneratedValue
    private int id;
    /**
     * 处理人
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User processUser;

    /*
     * 处理数量
     */
    @Column(scale = 2)
    private BigDecimal processCount;

    /**
     * 处理金额
     */
    @Column(scale = 3)
    private BigDecimal processedAmount;
    /**
     * 接受时间
     */
    private OffsetDateTime acceptDate;

    /**
     * 处理完成时间
     */
    private OffsetDateTime processedDate;

    // TODO: 报销单列表

}
