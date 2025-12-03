package pers.eastwind.billmanager.attach.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
}
