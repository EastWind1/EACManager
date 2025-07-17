package pers.eastwind.billmanager.model.dto;

import lombok.Data;
import pers.eastwind.billmanager.model.common.AttachmentType;

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
     * 文件类型
     */
    private AttachmentType type = AttachmentType.OTHER;
}
