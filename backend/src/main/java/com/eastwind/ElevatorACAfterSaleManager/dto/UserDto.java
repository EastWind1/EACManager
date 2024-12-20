package com.eastwind.ElevatorACAfterSaleManager.dto;

import com.eastwind.ElevatorACAfterSaleManager.entity.Authority;
import com.eastwind.ElevatorACAfterSaleManager.entity.User;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 用户DTO
 */
@Data
public class UserDto {
    /**
     * 用户ID
     */
    private int id;
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     * 除非必要，不允许向外传递
     */
    private String password;
    /**
     * 姓名
     */
    private String name;
    /**
     * 电子邮件
     */
    private String email;

    private Collection<? extends GrantedAuthority> authorities;

    /**
     * 从实体转换为dto
     */
    public static UserDto convertFromUser(User user) {
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setUsername(user.getUsername());
        userDto.setPassword(user.getPassword());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setAuthorities(user.getAuthorities());
        return userDto;
    }

    /**
     * 从dto转换为实体
     */
    public static User convertToUser(UserDto userDto) {
        User user = new User();
        user.setUsername(userDto.username);
        user.setPassword(userDto.password);
        user.setEmail(userDto.email);

        List<Authority> authorities = new ArrayList<>();
        for (GrantedAuthority authority : userDto.authorities) {
            authorities.add((Authority) authority);
        }
        user.setAuthorities(authorities);

        return user;
    }
}