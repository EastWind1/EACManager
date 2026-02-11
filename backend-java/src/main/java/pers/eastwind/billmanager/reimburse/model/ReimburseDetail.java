package pers.eastwind.billmanager.reimburse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 报销单明细
 */
@Entity
@Data
public class ReimburseDetail {
    @Id
    @Column(insertable = false, updatable = false)
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
