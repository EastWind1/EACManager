package com.eastwind.EACAfterSaleMgr.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 服务项目明细
 */
@Entity
@Data
public class ServiceBillDetail {
    @Id
    @GeneratedValue
    private Integer id;
    /**
     * 设备类型
     */
    private String device;
    /**
     * 数量
     */
    private BigDecimal quantity;
    /**
     * 单价
     */
    private BigDecimal unitPrice;
    /**
     * 小计
     */
    private BigDecimal subtotal;
    /**
     * 备注
     */
    private String remark;
}
