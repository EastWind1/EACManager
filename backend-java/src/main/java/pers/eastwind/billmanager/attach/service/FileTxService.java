package pers.eastwind.billmanager.attach.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.config.AttachPaths;
import pers.eastwind.billmanager.attach.model.FileOp;
import pers.eastwind.billmanager.attach.model.FileOpType;
import pers.eastwind.billmanager.common.exception.FileOpException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件事务：一组文件操作要么全部完成，要么回滚已完成的部分。
 */
@Slf4j
@Service
public class FileTxService {
    private final AttachPaths paths;

    public FileTxService(AttachPaths paths) {
        this.paths = paths;
    }

    private void validTarget(Path path, boolean exists) {
        if (path == null) {
            throw new FileOpException("目标不能为空");
        }
        if (exists) {
            if (!Files.exists(path)) {
                throw new FileOpException("目标不存在: " + path);
            }
        } else if (Files.exists(path)) {
            throw new FileOpException("目标已存在: " + path);
        }
    }

    private void validOrigin(Path path) {
        if (path == null) {
            throw new FileOpException("源不能为空");
        }
        if (!Files.exists(path)) {
            throw new FileOpException("源不存在: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new FileOpException("源必须是文件: " + path);
        }
    }

    public void exec(List<FileOp> ops) {
        List<FileOp> executedOps = new ArrayList<>();
        // 软删除时暂存到附件临时目录，事务提交成功后立即删除，避免堆积
        List<Path> softDeleted = new ArrayList<>();
        try {
            for (FileOp op : ops) {
                switch (op.type()) {
                    case CREATE -> {
                        validTarget(op.target(), false);
                        PathUtils.createParentDirectories(op.target());
                        Files.createFile(op.target());
                        executedOps.add(op);
                    }
                    case COPY -> {
                        validOrigin(op.origin());
                        validTarget(op.target(), false);
                        PathUtils.createParentDirectories(op.target());
                        Files.copy(op.origin(), op.target());
                        executedOps.add(op);
                    }
                    case MOVE -> {
                        validOrigin(op.origin());
                        validTarget(op.target(), false);
                        PathUtils.createParentDirectories(op.target());
                        Files.move(op.origin(), op.target());
                        executedOps.add(op);
                    }
                    case DELETE -> {
                        validTarget(op.target(), true);
                        // 软删除：先移到临时目录，提交成功后立即删除
                        Path fileName = op.target().getFileName();
                        Path temp = Files.createTempFile(paths.temp(), fileName.toString(), null);
                        Files.move(op.target(), temp, StandardCopyOption.REPLACE_EXISTING);
                        executedOps.add(new FileOp(FileOpType.MOVE, op.target(), temp));
                        softDeleted.add(temp);
                    }
                    default -> throw new FileOpException("不支持的操作: " + op.type());
                }
            }
        } catch (IOException e) {
            rollback(executedOps);
            throw new FileOpException("文件事务执行失败", e);
        }
        for (Path temp : softDeleted) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                log.warn("删除软删除临时文件失败: {}", temp, e);
            }
        }
        log.debug("文件事务提交: {} 个操作", executedOps.size());
    }

    private void rollback(List<FileOp> executedOps) {
        List<Throwable> rollbackErrors = new ArrayList<>();
        // 倒序回滚
        for (int i = executedOps.size() - 1; i >= 0; i--) {
            var op = executedOps.get(i);
            switch (op.type()) {
                case CREATE, COPY -> {
                    try {
                        Files.delete(op.target());
                    } catch (Exception e) {
                        rollbackErrors.add(e);
                    }
                }
                case MOVE -> {
                    try {
                        Files.move(op.target(), op.origin());
                    } catch (Exception e) {
                        rollbackErrors.add(e);
                    }
                }
                default -> rollbackErrors.add(new FileOpException("不支持的回滚操作: " + op.type()));
            }
        }
        if (!rollbackErrors.isEmpty()) {
            log.error("文件事务回滚失败: {}", executedOps);
            for (Throwable e : rollbackErrors) {
                log.error("", e);
            }
        }
    }
}
