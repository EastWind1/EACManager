package pers.eastwind.billmanager.attach.util;

import pers.eastwind.billmanager.common.exception.BizException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * 安全路径
 * 
 * <p>每次拼接都会校验是否有路径穿越</p>
 */
public final class SafePath {
    private final Path root;
    private final Path path;

    private SafePath(Path root, Path path) {
        this.root = root;
        this.path = path;
    }

    /**
     * 以 root 为根创建 SafePath
     * @param root 根路径
     * @return
     */
    public static SafePath of(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        return new SafePath(normalized, normalized);
    }


    /**
     * 以 root 为根创建 SafePath
     * @param root 根路径
     * @return
     */
    public static SafePath of(String root) {
        return SafePath.of(Path.of(root));
    }

    public Path toPath() {
        return path;
    }

    public SafePath resolve(String other) {
        try {
            return resolve(Path.of(other));
        } catch (InvalidPathException e) {
            throw new BizException("非法路径", e);
        }
    }

    public SafePath resolve(Path other) {
        Path resolved = path.resolve(other).normalize();
        if (!resolved.startsWith(root)) {
            throw new BizException("非法路径");
        }
        return new SafePath(root, resolved);
    }

    public SafePath normalize() {
        Path normalized = path.normalize();
        if (!normalized.startsWith(root)) {
            throw new BizException("非法路径");
        }
        return new SafePath(root, normalized);
    }

    public Path getFileName() {
        return path.getFileName();
    }

    public Path getParent() {
        return path.getParent();
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
