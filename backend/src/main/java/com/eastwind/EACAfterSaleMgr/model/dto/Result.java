package com.eastwind.EACAfterSaleMgr.model.dto;

/**
 * Controller 响应结果
 * 由 HTTP 响应码反映成功失败
 *
 * @param message 响应信息
 * @param data 数据
 * @param <T> 数据类型
 */
public record Result<T>(String message, T data) {
    /**
     * 创建成功结果
     *
     * @param data 数据
     * @param <T> 数据类型
     * @return 结果
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", data);
    }

    /**
     * 创建失败结果
     * @param message 错误信息
     * @return 结果
     */
    public static  Result<Object> error(String message) {
        return new Result<>( message, null);
    }
}