package pers.eastwind.billmanager.servicebill.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 仅保存附件 ID，不建立实体关联，避免附件删除时牵连本模块的持久化状态。
 */
@Entity
@Data
@Table(name = "service_bill_attach_relation",
        indexes = @Index(columnList = "serviceBillId"))
public class ServiceBillAttach {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer serviceBillId;
    private Integer attachId;
}
