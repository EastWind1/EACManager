package pers.eastwind.billmanager.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
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
    @GeneratedValue
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
