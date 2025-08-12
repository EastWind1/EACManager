package pers.eastwind.billmanager.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
     * 摘要
     */
    private String summary;
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    /**
     * 发生日期
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
     * 附件
     */
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "reimbursement_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private List<Attachment> attachments = new ArrayList<>();
}
