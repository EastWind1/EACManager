package pers.eastwind.billmanager.servicebill.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.company.model.Company;
import pers.eastwind.billmanager.security.model.AuditEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(indexes = {
        @Index(columnList = "number"),
        @Index(columnList = "created_date")
})
public class ServiceBill extends AuditEntity {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String number;
    @Enumerated
    private ServiceBillType type = ServiceBillType.INSTALL;
    @Enumerated
    private ServiceBillState state = ServiceBillState.CREATED;
    @ManyToOne
    @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Company productCompany;
    private String projectName;
    private String projectAddress;
    private String projectContact;
    private String projectContactPhone;
    private String onSiteContact;
    private String onSitePhone;
    private String elevatorInfo;
    @Column(scale = 3)
    private BigDecimal totalAmount;
    private Instant orderDate;
    private Instant processedDate;
    private Instant finishedDate;
    @Column(length = 1000)
    private String remark;
    /**
     * 明细由子端维护关联（mappedBy），插入时带外键；orphanRemoval 保证移除的明细真正删除。
     */
    @OneToMany(mappedBy = "serviceBill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceBillDetail> details = new ArrayList<>();
    /**
     * 不要给默认值：Spring Data 以非 null 版本判定实体已存在，save() 会走 merge。
     */
    @Version
    private Integer version;
}

