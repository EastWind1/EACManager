package pers.eastwind.billmanager.attach.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.common.model.AuditEntity;

/**
 * 附件
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
public class Attachment extends AuditEntity {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}
