package pers.eastwind.billmanager.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
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
    private static final String ACCESS_COOKIE = "X-Auth-Token";
    private static final String ACCESS_PATH = "/api";

    private final JWTProperties config;
    private final UserService userService;

    public UserController(JWTProperties config, UserService userService) {
        this.config = config;
        this.userService = userService;
    }

    public record LoginParam(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    @PostMapping("/token")
    public ResponseEntity<UserDTO> login(@Valid @RequestBody LoginParam param) {
        return withToken(userService.login(param.username, param.password));
    }

    @GetMapping("/initialization")
    public Map<String, Boolean> initializationStatus() {
        return Map.of("required", userService.isInitializationRequired());
    }

    @PostMapping("/initialization")
    public UserDTO initialize(@Valid @RequestBody UserDTO param) {
        return userService.initialize(param);
    }

    @PutMapping("/logout")
    public ResponseEntity<Object> logout(
            @CookieValue(name = ACCESS_COOKIE, required = false) String accessToken) {
        userService.logout(accessToken);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                expiredCookie(ACCESS_COOKIE, ACCESS_PATH).toString()).build();
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

    private ResponseEntity<UserDTO> withToken(LoginResult res) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookie(ACCESS_COOKIE, res.token(), ACCESS_PATH, config.getExpire()).toString())
                .body(res.user());
    }

    private ResponseCookie cookie(String name, String value, String path, long maxAge) {
        return ResponseCookie.from(name, value)
                .path(path)
                .httpOnly(true)
                .secure(true)
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();
    }

    private ResponseCookie expiredCookie(String name, String path) {
        return cookie(name, "", path, -1);
    }
}
