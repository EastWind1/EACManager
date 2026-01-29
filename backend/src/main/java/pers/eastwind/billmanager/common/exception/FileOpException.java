package pers.eastwind.billmanager.common.exception;

import lombok.Getter;

import java.nio.file.Path;

/**
 * 文件操作异常
 */
@Getter
public class FileOpException extends RuntimeException {
    private Path path;
    public FileOpException(Throwable cause) {
        super(cause);
    }
    public FileOpException(String message) {
        super(message);
    }
    public FileOpException(String message, Throwable cause) {
        super(message, cause);
    }
    public FileOpException(String message, Path path) {
        super(message);
        this.path = path;
    }
    public FileOpException(String message, Path path, Throwable cause) {
        super(message, cause);
        this.path = path;
    }
    @Override
    public String getMessage() {
        return super.getMessage() + (path != null ? "，路径：" + path : "");
    }
}
