package pers.eastwind.billmanager.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.model.common.AttachmentType;

/**
 * 附件
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
public class Attachment extends AuditEntity {
    @Id
    @GeneratedValue
    private Integer id;
    /**
     * 文件名
     */
    private String name;
    /**
     * 文件类型
     */
    @Enumerated
    private AttachmentType type = AttachmentType.OTHER;
    /**
     * 文件相对路径
     */
    private String relativePath;
    /**
     * 关联服务单
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ServiceBill serviceBill;
    /**
     * 关联报销单
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Reimbursement reimbursement;
}
