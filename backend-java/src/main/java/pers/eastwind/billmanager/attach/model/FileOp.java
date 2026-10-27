package pers.eastwind.billmanager.attach.model;

import java.nio.file.Path;

/**
 * 文件操作；origin 仅在移动/复制时有效。
 */
public record FileOp(FileOpType type, Path origin, Path target) {
}
