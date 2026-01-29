package pers.eastwind.billmanager.attach.model;

import lombok.Data;

/**
 * 附件 DTO
 */
@Data
public class AttachmentDTO {
    private Integer id;
    /**
     * 文件名
     */
    private String name;
    /**
     * 相对路径
     */
    private String relativePath;
    /**
     * 是否临时文件
     */
    private boolean temp;
    /**
     * 文件类型
     */
    private AttachmentType type = AttachmentType.OTHER;
}
