package pers.eastwind.billmanager.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pers.eastwind.billmanager.model.dto.LoginResult;
import pers.eastwind.billmanager.model.dto.UserDTO;
import pers.eastwind.billmanager.service.UserService;

import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
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
        LoginResult res = userService.login(param.username, param.password, 24 * 60 * 60);
        return ResponseEntity.ok().header("Set-Cookie",
                ResponseCookie.from("X-Auth-Token", res.token())
                        .path("/")
                        .httpOnly(true)
                        .secure(true)
                        .maxAge(3600)
                        .sameSite("Strict").build().toString())
                .body(res.user());
    }

    /**
     * 查询
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @GetMapping
    public List<UserDTO> getAll() {
        return userService.getAll();
    }

    /**
     * 创建
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public UserDTO create(@RequestBody UserDTO user) {
        return userService.create(user);
    }

    /**
     * 修改
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @PutMapping
    public UserDTO update(@RequestBody UserDTO user) {
        return userService.update(user);
    }

    /**
     * 禁用
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void disable(@PathVariable Integer id) {
        userService.disable(id);
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
