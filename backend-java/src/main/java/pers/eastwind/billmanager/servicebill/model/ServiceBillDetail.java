package pers.eastwind.billmanager.servicebill.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Data
public class ServiceBillDetail {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String device;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String remark;
    /**
     * 关联维护方（父实体 details 为 mappedBy），插入时外键随行写入；equals/hashCode/toString 排除以避免递归。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_bill_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ServiceBill serviceBill;
}
