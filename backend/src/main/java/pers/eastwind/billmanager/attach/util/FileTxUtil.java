package pers.eastwind.billmanager.attach.util;

import org.apache.commons.io.file.PathUtils;
import pers.eastwind.billmanager.attach.model.FileOp;
import pers.eastwind.billmanager.attach.model.FileOpType;
import pers.eastwind.billmanager.common.exception.FileOpException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件事务工具类
 */
public class FileTxUtil {
    /**
     * 校验目标路径
     * @param created 将创建的文件
     * @param deleted 将删除的文件
     * @param path 目标文件
     * @param exists 是否存在
     */
    private static void validTarget(Set<Path> created, Set<Path> deleted, Path path, boolean exists) {
        if (path == null) {
            throw new FileOpException("目标不能为空");
        }
        Path parent = path.getParent();

        if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
            throw new FileOpException("没有操作目标父目录的写权限: " + parent);
        }
        if (exists) {
            if (deleted.contains(path) || !created.contains(path) && !Files.exists(path)) {
                throw new FileOpException("目标不存在: " + path);
            }
        } else {
            if (created.contains(path) || !deleted.contains(path) && Files.exists(path)) {
                throw new FileOpException("目标已存在: " + path);
            }
        }
    }
    /**
     * 校验源路径
     * @param created 将创建的路径
     * @param deleted 将删除的路径
     * @param path 目标路径
     */
    private static void validOrigin(Set<Path> created, Set<Path> deleted, Path path) {
        if (path == null) {
            throw new FileOpException("源不能为空");
        }
        if (deleted.contains(path) || !created.contains(path) && !Files.exists(path)) {
            throw new FileOpException("源不存在: " + path);
        }
        if (!created.contains(path)) {
            if (Files.isDirectory(path)) {
                throw new FileOpException("源必须是文件: " + path);
            }
            if (!Files.isReadable(path)) {
                throw new FileOpException("没有源文件的读权限: " + path);
            }
        }
    }
    /**
     * 校验文件操作
     * @param ops 文件操作列表
     */
    private static void valid(List<FileOp> ops) {
        Set<Path> created = new HashSet<>();
        Set<Path> deleted = new HashSet<>();
        for (FileOp op : ops) {
            switch (op.type()) {
                case CREATE -> {
                    validTarget(created,deleted, op.target(), false);
                    created.add(op.target());
                    deleted.remove(op.target());
                }
                case COPY -> {
                    validOrigin(created, deleted, op.origin());
                    validTarget(created, deleted, op.target(), false);
                    created.add(op.target());
                    deleted.remove(op.target());
                }
                case MOVE -> {
                    validOrigin(created, deleted, op.origin());
                    validTarget(created, deleted, op.target(), false);
                    created.add(op.target());
                    deleted.remove(op.target());
                    created.remove(op.origin());
                    deleted.add(op.origin());
                }
                case DELETE -> {
                    validTarget(created, deleted, op.target(), true);
                    created.remove(op.target());
                    deleted.add(op.target());
                }
            }
        }
    }
    /**
     * 执行文件操作
     * @param ops 文件操作
     */
    public static void exec(List<FileOp> ops)  {
        valid(ops);
        List<FileOp> executedOps = new ArrayList<>();
        try {
            for (FileOp op : ops) {
                switch (op.type()) {
                    case CREATE -> {
                        PathUtils.createParentDirectories(op.target());
                        Files.createFile(op.target());
                        executedOps.add(op);
                    }
                    case COPY -> {
                        PathUtils.createParentDirectories(op.target());
                        Files.copy(op.origin(), op.target());
                        executedOps.add(op);
                    }
                    case MOVE -> {
                        PathUtils.createParentDirectories(op.target());
                        Files.move(op.origin(), op.target());
                        executedOps.add(op);
                    }
                    case DELETE -> {
                        // 软删除
                        Path fileName = op.target().getFileName();
                        Path temp = Files.createTempFile(fileName.toString(), null);
                        temp.toFile().deleteOnExit();
                        Files.move(op.target(), temp);
                        executedOps.add(new FileOp(FileOpType.MOVE, op.target(), temp));
                    }
                    default -> throw new FileOpException("不支持的操作: " + op.type());
                }
            }
        } catch (IOException e) {
            rollback(executedOps);
            throw new FileOpException("文件事务执行失败", e);
        }
    }
    /**
     * 回滚文件操作
     * @param executedOps 文件操作
     */
    private static void rollback(List<FileOp> executedOps) {
        try {
            // 倒序回滚
            for (int i = executedOps.size() - 1; i >= 0; i--) {
                var op = executedOps.get(i);
                switch (op.type()) {
                    case CREATE, COPY -> {
                        Files.delete(op.target());
                    }
                    case MOVE -> {
                        Files.move(op.target(), op.origin());
                    }
                    default -> throw new FileOpException("不支持的回滚操作: " + op.type());
                }
            }
        } catch (Exception e) {
            throw new FileOpException("文件事务回滚失败", e);
        }
    }
}
