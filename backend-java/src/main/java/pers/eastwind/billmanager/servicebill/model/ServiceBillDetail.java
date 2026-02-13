package pers.eastwind.billmanager.servicebill.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 服务项目明细
 */
@Entity
@Data
public class ServiceBillDetail {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
