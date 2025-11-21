package pers.eastwind.billmanager.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import pers.eastwind.billmanager.model.dto.Result;
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
        String packageName = returnType.getContainingClass().getPackage().getName();
        boolean isBusController = packageName.startsWith("pers.eastwind.billmanager.controller");
        return isBusController && returnType.getMethod() != null && returnType.getMethod().getReturnType() != Resource.class;
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
                return jsonMapper.writeValueAsString(Result.ok(body));
            } catch (JacksonException e) {
                throw new RuntimeException("序列化失败");
            }
        }
        return Result.ok(body);
    }
}
