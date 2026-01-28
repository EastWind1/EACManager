package pers.eastwind.billmanager.attach.model;

import java.nio.file.Path;

/**
 * 上传结果
 * @param filename 文件名
 * @param type     类型
 * @param path     相对路径
 */
public record UploadResult(String filename, AttachmentType type, Path path) {
}
