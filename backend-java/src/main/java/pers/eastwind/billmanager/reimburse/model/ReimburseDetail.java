package pers.eastwind.billmanager.reimburse.model;

import jakarta.persistence.*;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
