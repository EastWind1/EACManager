package pers.eastwind.billmanager.reimburse.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.security.model.AuditEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 类名简化为 Reimburse，表名仍为 reimbursement，故显式指定。
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "reimbursement",
        indexes = {@Index(columnList = "number"),
                @Index(columnList = "reimburse_date")})
public class Reimburse extends AuditEntity {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String number;
    private ReimburseState state;
    private String summary;
    private BigDecimal totalAmount;
    private Instant reimburseDate;
    private String remark;

    /**
     * 明细由子端维护关联（mappedBy），插入时带外键；orphanRemoval 保证移除的明细真正删除。
     */
    @OneToMany(mappedBy = "reimburse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReimburseDetail> details = new ArrayList<>();
    @Version
    private Integer version;
}
