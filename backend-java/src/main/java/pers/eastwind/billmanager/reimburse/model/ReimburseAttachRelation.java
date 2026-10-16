package pers.eastwind.billmanager.reimburse.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 报销单附件关联
 */
@Entity
@Data
@Table(indexes = @Index(columnList = "reimburseId"))
public class ReimburseAttachRelation {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    /**
     * 报销单 ID
     */
    private Integer reimburseId;
    /**
     * 附件 ID
     */
    private Integer attachId;
}
