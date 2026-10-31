package pers.eastwind.billmanager.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.autoconfigure.error.AbstractErrorController;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import pers.eastwind.billmanager.common.model.Result;

/**
 * 全局异常处理，处理非 Controller 抛出的异常
 */
@Slf4j
@Controller
@RequestMapping("${server.error.path:${error.path:/error}}")
public class GlobalErrorController extends AbstractErrorController {

    public GlobalErrorController(ErrorAttributes errorAttributes) {
        super(errorAttributes);
    }

    @RequestMapping
    public ResponseEntity<Result<Object>> error(HttpServletRequest request) {
        HttpStatus status = this.getStatus(request);
        Throwable throwable = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
        if (throwable != null) {
            log.error("请求处理失败: {} {}", request.getMethod(), request.getRequestURI(), throwable);
        } else {
            log.error("请求处理失败: {} {}", request.getMethod(), request.getRequestURI());
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.error("服务器异常"));
    }
}
