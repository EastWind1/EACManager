package pers.eastwind.billmanager.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import pers.eastwind.billmanager.model.dto.Result;

/**
 * 全局异常处理，包括 Filter，401，404
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
        Throwable throwable = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
        if (status == HttpStatus.NO_CONTENT || throwable == null || throwable.getCause() == null) {
            return new ResponseEntity<>(Result.error("服务器异常"), status);
        } else {
            return new ResponseEntity<>(Result.error(throwable.getCause().getMessage()), status);
        }
    }
}