package pers.eastwind.billmanager.attach.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.file.Path;

/**
 * 已完成访问校验的附件读取结果：对外 DTO 与本地磁盘路径。
 * 不携带实体，避免 relative_path 等内部字段流出附件模块。
 */
public record AttachFile(AttachDTO attachment, @JsonIgnore Path path) {
}
