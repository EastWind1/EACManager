package com.eastwind.EACAfterSaleMgr.service;

import com.eastwind.EACAfterSaleMgr.model.entity.User;
import com.eastwind.EACAfterSaleMgr.repository.UserRepository;
import com.eastwind.EACAfterSaleMgr.util.JWTUtil;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务
 */
@Service
public class UserService implements UserDetailsService {
    private final JWTUtil jwtUtil;
    private final UserRepository userRepository;
    public UserService(JWTUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    public User create(User user) {
        if (userRepository.existsUserByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        return userRepository.save(user);
    }

    public User update(User user) {
        return userRepository.save(user);
    }

    @Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("未找到用户名为 " + username + " 的用户");
        }
        return user;
    }

    public String login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user == null || !user.isEnabled()) {
            throw new RuntimeException("用户不存在");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        return jwtUtil.generateToken(username);
    }

}
