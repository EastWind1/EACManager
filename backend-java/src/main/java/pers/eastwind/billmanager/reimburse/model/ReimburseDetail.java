package pers.eastwind.billmanager.reimburse.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Data
public class ReimburseDetail {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    private BigDecimal amount;

    /**
     * 关联维护方（父实体 details 为 mappedBy）；equals/hashCode/toString 排除以避免与父实体递归。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reimbursement_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Reimburse reimburse;
}
