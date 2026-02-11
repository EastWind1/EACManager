package pers.eastwind.billmanager.attach.model;

import java.nio.file.Path;

/**
 * 文件操作
 * @param type 操作类型
 * @param origin 源文件, 只有移动和复制时有效
 * @param target 目标文件
 */
public record FileOp(FileOpType type, Path origin, Path target) {
}
