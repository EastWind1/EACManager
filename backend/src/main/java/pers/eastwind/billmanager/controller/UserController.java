package pers.eastwind.billmanager.controller;

import pers.eastwind.billmanager.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 登录参数
     * @param username 用户名
     * @param password 密码
     */
    public record LoginParam(String username, String password) {}

    /**
     * 登录
     * @param param 登录参数
     * @return token
     */
    @PostMapping("/token")
    public String login(@RequestBody LoginParam param) {
        return userService.login(param.username, param.password);
    }
}
