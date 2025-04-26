package com.eastwind.EACAfterSaleMgr.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 服务项目明细DTO
 */
@Data
public class ServiceBillDetailDTO {
    private Integer id;
    /**
     * 设备类型
     */
    private String device;
    /**
     * 数量
     */
    private int quantity;
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
