package pers.eastwind.billmanager.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.eastwind.billmanager.security.service.AuthenticateService;

/**
 * 账户禁用属于鉴权领域，路由保留在 /api/user 下以兼容前端。
 */
@RestController
@RequestMapping("/api/user")
public class AccountController {
    private final AuthenticateService authenticateService;

    public AccountController(AuthenticateService authenticateService) {
        this.authenticateService = authenticateService;
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasAuthority('user:disable')")
    public void disable(@PathVariable String username) {
        authenticateService.disable(username);
    }
}
