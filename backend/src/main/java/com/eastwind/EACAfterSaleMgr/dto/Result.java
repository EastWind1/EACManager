package com.eastwind.EACAfterSaleMgr.dto;

/**
 * Controller 响应结果
 * @param code 响应代码
 * @param message 响应信息
 * @param data 数据
 * @param <T> 数据类型
 */
public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "OK", data);
    }
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}