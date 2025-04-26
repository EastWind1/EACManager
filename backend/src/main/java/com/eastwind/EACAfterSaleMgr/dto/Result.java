package com.eastwind.EACAfterSaleMgr.dto;

/**
 * Controller 响应结果
 * 由 HTTP 响应码反映成功失败
 *
 * @param message 响应信息
 * @param data 数据
 * @param <T> 数据类型
 */
public record Result<T>(String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", data);
    }
    public static <T> Result<T> error(String message) {
        return new Result<>( message, null);
    }
}