package pers.eastwind.billmanager.reimburse.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.common.model.AuditEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 报销单
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(indexes = {@Index(columnList = "number"),
        @Index(columnList = "reimburse_date")})
public class Reimbursement extends AuditEntity {
    @Id
    @GeneratedValue
    private Integer id;
    /**
     * 编号
     */
    private String number;
    /**
     * 状态
     */
    private ReimburseState state;
    /**
     * 摘要
     */
    private String summary;
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    /**
     * 报销日期
     */
    private Instant reimburseDate;
    /**
     * 备注
     */
    private String remark;

    /**
     * 明细
     */
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "reimbursement_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private List<ReimburseDetail> details = new ArrayList<>();
    /**
     * 版本字段
     */
    @Version
    private Integer version = 0;
}
