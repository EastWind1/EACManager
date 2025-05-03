package com.eastwind.EACAfterSaleMgr.controller;

import com.eastwind.EACAfterSaleMgr.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record LoginParam(String username, String password) {
    }

    @PostMapping("token")
    public String login(@RequestBody LoginParam param) {
        return userService.login(param.username(), param.password());
    }
}
