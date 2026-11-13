package pers.eastwind.billmanager.servicebill.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.security.model.AuditEntity;

/**
 * 服务单负责人关联。
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(
        indexes = {
                @Index(columnList = "service_bill_id"),
                @Index(columnList = "user_id")
        }
)
public class ServiceBillAssignee extends AuditEntity {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer serviceBillId;

    private Integer userId;
}
