package com.eastwind.EACAfterSaleMgr.controller;

import com.eastwind.EACAfterSaleMgr.model.dto.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * RestController 增强
 */
@Slf4j
@RestControllerAdvice
public class ControllerAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper objectMapper;

    public ControllerAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 对所有 RestController 响应x进行包装
     */
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // 避免二次处理
        if (body instanceof Result) {
            return body;
        }
        // 处理 String
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(Result.ok(body));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("序列化失败");
            }
        }
        return Result.ok(body);
    }

    /**
     * 处理业务层异常
     * @param e 异常
     * @return 结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleException(Exception e) {
        log.error(e.getMessage(), e);
        return Result.error(e.getMessage());
    }
}
