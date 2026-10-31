package pers.eastwind.billmanager.user.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.security.config.JWTProperties;
import pers.eastwind.billmanager.user.model.LoginResult;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final JWTProperties config;
    private final UserService userService;

    public UserController(JWTProperties config, UserService userService) {
        this.config = config;
        this.userService = userService;
    }

    @PostMapping("/token")
    public ResponseEntity<UserDTO> login(@RequestBody LoginParam param) {
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
    public UserDTO initialize(@RequestBody UserDTO param) {
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
    public UserDTO create(@RequestBody UserDTO user) {
        return userService.create(user);
    }

    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.user.permission.UserPermission).UPDATE.getCode())")
    @PutMapping
    public UserDTO update(@RequestBody UserDTO user) {
        return userService.update(user);
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasAuthority(T(pers.eastwind.billmanager.user.permission.UserPermission).DISABLE.getCode())")
    public void disable(@PathVariable String username) {
        userService.disable(username);
    }

    public record LoginParam(String username, String password) {
    }
}
