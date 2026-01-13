package pers.eastwind.billmanager.user.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
        long expiresSeconds = 7 * 24 * 60 * 60;
        LoginResult res = userService.login(param.username, param.password);
        return ResponseEntity.ok().header("Set-Cookie",
                        ResponseCookie.from("X-Auth-Token", res.token())
                                .path("/")
                                .httpOnly(true)
                                .secure(true)
                                .maxAge(expiresSeconds)
                                .sameSite("Strict").build().toString())
                .body(res.user());
    }

    /**
     * 查询
     */
    @GetMapping
    public List<UserDTO> getAll() {
        return userService.getAll();
    }

    /**
     * 创建
     */
    @PostMapping
    public UserDTO create(@RequestBody UserDTO user) {
        return userService.create(user);
    }

    /**
     * 修改
     */
    @PutMapping
    public UserDTO update(@RequestBody UserDTO user) {
        return userService.update(user);
    }

    /**
     * 禁用
     * @param username 用户名
     */
    @DeleteMapping("/{username}")
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
