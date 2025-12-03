package pers.eastwind.billmanager.reimburse.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 报销单明细 DTO
 */
@Data
public class ReimburseDetailDTO {
    private Integer id;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 金额
     */
    private BigDecimal amount;
}
