package pers.eastwind.billmanager.common.model;

/**
 * Controller 响应结果
 * 由 HTTP 响应码反映成功失败
 */
public record Result<T>(String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", data);
    }

    public static Result<Object> error(String message) {
        return new Result<>(message, null);
    }

    public static <T> Result<T> error(String message, T data) {
        return new Result<>(message, data);
    }
}