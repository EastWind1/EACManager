package pers.eastwind.billmanager.service;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pers.eastwind.billmanager.model.dto.LoginResult;
import pers.eastwind.billmanager.model.dto.UserDTO;
import pers.eastwind.billmanager.model.entity.AuthorityRole;
import pers.eastwind.billmanager.model.entity.User;
import pers.eastwind.billmanager.model.mapper.UserMapper;
import pers.eastwind.billmanager.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用户服务
 */
@Service
public class UserService implements UserDetailsService {
    private final JWTService jwtUtil;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(JWTService jwtUtil, UserRepository userRepository, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * 获取用户
     */
    public List<UserDTO> getAll() {
        User curUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (curUser.getAuthority() == AuthorityRole.ROLE_ADMIN) {
            return userRepository.findAllEnabled().stream().map(userMapper::toBaseDTO).toList();
        } else {
            User user = userRepository.findById(curUser.getId()).orElse(null);
            if (user == null) {
                return new ArrayList<>();
            }
            return List.of(userMapper.toBaseDTO(user));
        }
    }

    /**
     * 创建用户
     *
     * @param user 用户
     * @return 创建后的用户
     */
    @Transactional
    public UserDTO create(UserDTO user) {
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (userRepository.existsUserByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        User newUser = userMapper.toEntity(user);
        newUser.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        return userMapper.toDTO(userRepository.save(newUser));
    }

    /**
     * 更新用户
     *
     * @param user 用户
     * @return 更新后的用户
     */
    @Transactional
    public UserDTO update(UserDTO user) {
        if (user.getId() == null) {
            throw new RuntimeException("id不能为空");
        }
        User curUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (curUser.getAuthority() != AuthorityRole.ROLE_ADMIN && !curUser.getId().equals(user.getId())) {
            throw new AccessDeniedException("无权限修改其他用户信息");
        }

        User oldUser = userRepository.findById(user.getId()).orElse(null);
        if (oldUser == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!oldUser.getUsername().equals(user.getUsername())) {
            throw new RuntimeException("用户名不能修改");
        }
        if (user.getPassword() != null) {
            if (user.getPassword().isEmpty()) {
                throw new RuntimeException("密码不能为空");
            }
            oldUser.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        }
        userMapper.updateEntityFromDTO(user, oldUser);
        return userMapper.toDTO(userRepository.save(oldUser));
    }

    /**
     * 禁用用户
     *
     * @param id 用户id
     */
    @Transactional
    public void disable(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setEnabled(false);
        userRepository.save(user);
    }

    @Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("未找到用户名为 " + username + " 的用户");
        }
        return user;
    }

    /**
     * 登录，生成 token
     *
     * @param username       用户名
     * @param password       密码
     * @param expiresSeconds token 过期秒数
     * @return 登录结果
     */
    public LoginResult login(String username, String password, long expiresSeconds) {
        User user = userRepository.findByUsername(username);
        if (user == null || !user.isEnabled()) {
            throw new RuntimeException("用户不存在");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        String subject = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest().getHeader(HttpHeaders.HOST);
        String token = jwtUtil.generateToken(username, subject, expiresSeconds);
        return new LoginResult(token, userMapper.toDTO(user));
    }

}
