package pers.eastwind.billmanager.user.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.user.config.UserConfigProperties;
import pers.eastwind.billmanager.user.model.LoginResult;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.service.UserService;

import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserConfigProperties config;
    private final UserService userService;

    public UserController(UserConfigProperties config, UserService userService) {
        this.config = config;
        this.userService = userService;
    }

    /**
     * 登录
     * 设置 token 至响应头
     *
     * @param param 登录参数
     * @return 用户信息
     */
    @PostMapping("/token")
    public ResponseEntity<UserDTO> login(@RequestBody LoginParam param) {
        LoginResult res = userService.login(param.username, param.password);
        return ResponseEntity.ok().header("Set-Cookie",
                        ResponseCookie.from("X-Auth-Token", res.token())
                                .path("/")
                                .httpOnly(true)
                                .secure(true)
                                .maxAge(config.getExpire())
                                .sameSite("Strict").build().toString())
                .body(res.user());
    }

    /**
     * 查询
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'FINANCE')")
    @GetMapping
    public PageResult<UserDTO> getAll(QueryParam queryParam) {
        return userService.getAll(queryParam);
    }

    /**
     * 创建
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping
    public UserDTO create(@RequestBody UserDTO user) {
        return userService.create(user);
    }

    /**
     * 修改
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'FINANCE')")
    @PutMapping
    public UserDTO update(@RequestBody UserDTO user) {
        return userService.update(user);
    }

    /**
     * 禁用
     * @param username 用户名
     */
    @DeleteMapping("/{username}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public void disable(@PathVariable String username) {
        userService.disable(username);
    }

    /**
     * 登录参数
     *
     * @param username 用户名
     * @param password 密码
     */
    public record LoginParam(String username, String password) {
    }
}
