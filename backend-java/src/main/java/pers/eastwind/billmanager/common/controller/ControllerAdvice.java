package pers.eastwind.billmanager.common.controller;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.exception.FileOpException;
import pers.eastwind.billmanager.common.model.Result;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * RestController 增强, 包装响应体
 */
@Slf4j
@RestControllerAdvice
public class ControllerAdvice implements ResponseBodyAdvice<Object> {
    private final JsonMapper jsonMapper;

    public ControllerAdvice(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 除文件下载，对响应进行包装
     */
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return returnType.getMethod() != null && returnType.getMethod().getReturnType() != Resource.class;
    }

    /**
     * 响应体包装
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // 避免二次处理, 不处理文件下载
        if (body instanceof Result || body instanceof Resource) {
            return body;
        }
        // 处理 String, String 内部由特定转换器处理，期望返回为 String，若直接返回 Result 会报类型转换错误
        if (body instanceof String) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return jsonMapper.writeValueAsString(Result.ok(body));
            } catch (JacksonException e) {
                throw new BizException("序列化失败");
            }
        }
        return Result.ok(body);
    }

    /**
     * 乐观锁异常处理
     */
    @ExceptionHandler(OptimisticLockException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleOptimisticLockException(OptimisticLockException e) {
        log.warn("数据已被更改", e);
        return Result.error("数据已被更改，请稍后刷新重试");
    }
    /**
     * 业务异常处理
     * @param e 错误
     * @return 错误结果
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleBizException(BizException e) {
        log.error(e.getMessage(),e);
        return Result.error(e.getMessage());
    }
    /**
     * 文件操作异常处理
     * @param e 错误
     * @return 错误结果
     */
    @ExceptionHandler(FileOpException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleBizException(FileOpException e) {
        log.error(e.getMessage(),e);
        return Result.error("文件操作异常");
    }
}
