package com.eastwind.ElevatorACAfterSaleManager.service;

import com.eastwind.ElevatorACAfterSaleManager.entity.User;
import com.eastwind.ElevatorACAfterSaleManager.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 用户服务
 */
@Service
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    public UserService(UserRepository userRepository) {
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
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDetails user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("未找到用户名为 " + username + " 的用户");
        }
        return user;
    }
}
