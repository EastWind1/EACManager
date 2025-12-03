package pers.eastwind.billmanager.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.autoconfigure.error.AbstractErrorController;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import pers.eastwind.billmanager.common.model.Result;

/**
 * 全局异常处理，包括 Filter
 */
@Controller
@RequestMapping("${server.error.path:${error.path:/error}}")
public class GlobalErrorController extends AbstractErrorController {

    public GlobalErrorController(ErrorAttributes errorAttributes) {
        super(errorAttributes);
    }

    @RequestMapping
    public ResponseEntity<Result<Object>> error(HttpServletRequest request) {
        HttpStatus status = this.getStatus(request);
        Throwable throwable = ((Throwable) request.getAttribute("jakarta.servlet.error.exception"));
        String errorMessage = (String) request.getAttribute("jakarta.servlet.error.message");

        // 拿到内层实际异常
        if (throwable != null && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }

        // filter 中抛出的异常默认处理为 500，此处对登录异常单独改为 401
        if (throwable instanceof AuthenticationException) {
            status = HttpStatus.UNAUTHORIZED;
        }

        String message = "服务器异常";
        if (errorMessage != null && !errorMessage.isEmpty()) {
            message = errorMessage;
        } else if (throwable != null && throwable.getMessage() != null && !throwable.getMessage().isEmpty()) {
            message = throwable.getMessage();
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.error(message));
    }
}