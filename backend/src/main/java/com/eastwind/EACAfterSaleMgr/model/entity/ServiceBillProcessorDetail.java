package com.eastwind.EACAfterSaleMgr.model.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 服务单处理人明细
 */
@Entity
@Data
public class ServiceBillProcessorDetail {

    @Id
    @GeneratedValue
    private Integer id;
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
    private ZonedDateTime acceptDate;

    /**
     * 处理完成时间
     */
    private ZonedDateTime processedDate;

}
