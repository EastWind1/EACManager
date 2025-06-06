package pers.eastwind.billmanager.controller;

import pers.eastwind.billmanager.model.dto.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * RestController 增强, 包装响应体
 */
@Slf4j
@RestControllerAdvice
public class ControllerAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper objectMapper;

    public ControllerAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        // 避免二次处理
        if (body instanceof Result) {
            return body;
        }
        // 处理 String, 否则会报类型转换错误
        if (body instanceof String) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(Result.ok(body));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("序列化失败");
            }
        }
        return Result.ok(body);
    }
}
