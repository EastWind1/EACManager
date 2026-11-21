package pers.eastwind.billmanager.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.security.config.JWTProperties;
import pers.eastwind.billmanager.security.service.AuthenticateService;
import pers.eastwind.billmanager.user.model.LoginResult;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final JWTProperties config;
    private final UserService userService;
    private final AuthenticateService authenticateService;

    public UserController(JWTProperties config, UserService userService, AuthenticateService authenticateService) {
        this.config = config;
        this.userService = userService;
        this.authenticateService = authenticateService;
    }

    public record LoginParam(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    @PostMapping("/token")
    public ResponseEntity<UserDTO> login(@Valid @RequestBody LoginParam param) {
        LoginResult res = userService.login(param.username, param.password);
        return ResponseEntity.ok().header("Set-Cookie",
                        ResponseCookie.from("X-Auth-Token", res.token())
                                .path("/api")
                                .httpOnly(true)
                                .secure(true)
                                .maxAge(config.getExpire())
                                .sameSite("Strict").build().toString())
                .body(res.user());
    }

    @GetMapping("/initialization")
    public Map<String, Boolean> initializationStatus() {
        return Map.of("required", userService.isInitializationRequired());
    }

    @PostMapping("/initialization")
    public UserDTO initialize(@Valid @RequestBody UserDTO param) {
        return userService.initialize(param);
    }

    /**
     * TODO：此处登出仅销毁 cookie，还需要结合 token 作废实现
     */
    @PutMapping("/logout")
    public ResponseEntity<Object> logout() {
        return ResponseEntity.ok().header("Set-Cookie",
                ResponseCookie.from("X-Auth-Token", "")
                        .path("/api")
                        .httpOnly(true)
                        .secure(true)
                        .maxAge(-1)
                        .sameSite("Strict").build().toString()).build();
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.user.permission.UserPermission).QUERY.getCode())")
    @GetMapping
    public PageResult<UserDTO> getAll(QueryParam queryParam) {
        return userService.getAll(queryParam);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.user.permission.UserPermission).CREATE.getCode())")
    @PostMapping
    public UserDTO create(@Valid @RequestBody UserDTO user) {
        return userService.create(user);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.user.permission.UserPermission).UPDATE.getCode())")
    @PutMapping
    public UserDTO update(@Valid @RequestBody UserDTO user) {
        return userService.update(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.user.permission.UserPermission).DISABLE.getCode())")
    public void disable(@PathVariable Integer id) {
        authenticateService.disable(id);
    }
}
