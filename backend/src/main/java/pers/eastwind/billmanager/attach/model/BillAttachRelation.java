package pers.eastwind.billmanager.attach.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 单据附件关系表
 */
@Entity
@Data
@Table(indexes = @Index(columnList = "billId, billType"))
public class BillAttachRelation {
    @Id
    @GeneratedValue
    private Integer id;
    /**
     * 单据类型
     */
    private BillType billType;
    /**
     * 单据ID
     */
    private Integer billId;
    /**
     * 附件ID
     */
    @OneToOne
    @JoinColumn(name = "attach_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Attachment attach;
}
