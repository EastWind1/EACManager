package pers.eastwind.billmanager.servicebill.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 服务单附件关联
 *
 * <p>只保存附件 ID，不建立实体关联，避免附件被删除时牵连本模块的持久化状态。</p>
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
    /**
     * 服务单 ID
     */
    private Integer serviceBillId;
    /**
     * 附件 ID
     */
    private Integer attachId;
}
