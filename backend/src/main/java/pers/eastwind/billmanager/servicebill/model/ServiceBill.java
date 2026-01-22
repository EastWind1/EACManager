package pers.eastwind.billmanager.servicebill.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.common.model.AuditEntity;
import pers.eastwind.billmanager.company.model.Company;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务单据实体类
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(indexes = {
        @Index(columnList = "number"),
        @Index(columnList = "created_date")
})
public class ServiceBill extends AuditEntity {
    @Id
    @GeneratedValue
    private Integer id;
    /**
     * 单号
     */
    private String number;
    /**
     * 单据类型
     */
    @Enumerated
    private ServiceBillType type = ServiceBillType.INSTALL;
    /**
     * 单据状态
     */
    @Enumerated
    private ServiceBillState state = ServiceBillState.CREATED;
    /**
     * 生产公司
     */
    @ManyToOne
    @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Company productCompany;
    /**
     * 项目名称
     */
    private String projectName;
    /**
     * 项目地址
     */
    private String projectAddress;
    /**
     * 项目联系人
     */
    private String projectContact;
    /**
     * 项目联系人电话
     */
    private String projectContactPhone;
    /**
     * 现场联系人
     */
    private String onSiteContact;
    /**
     * 现场联系人电话
     */
    private String onSitePhone;
    /**
     * 电梯信息
     */
    private String elevatorInfo;
    /**
     * 总金额
     */
    @Column(scale = 3)
    private BigDecimal totalAmount;
    /**
     * 下单时间
     */
    private Instant orderDate;
    /**
     * 完工时间
     */
    private Instant processedDate;
    /**
     * 完成时间
     */
    private Instant finishedDate;
    /**
     * 备注
     */
    @Column(length = 1000)
    private String remark;
    /**
     * 服务明细
     */
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "service_bill_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private List<ServiceBillDetail> details = new ArrayList<>();
    /**
     * 版本字段
     */
    @Version
    private Integer version = 0;
}

