package pers.eastwind.billmanager.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import pers.eastwind.billmanager.model.common.BillType;

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
    private Integer attachId;
}
