package com.eastwind.EACAfterSaleMgr.model.entity;

import com.eastwind.EACAfterSaleMgr.model.common.AttachmentType;
import jakarta.persistence.*;
import lombok.Data;

/**
 * 附件
 */
@Entity
@Data
public class Attachment {
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
}
