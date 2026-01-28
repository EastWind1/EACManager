package pers.eastwind.billmanager.user.service;

import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.user.model.AuthorityRole;
import pers.eastwind.billmanager.user.util.AuthUtil;
import pers.eastwind.billmanager.user.model.*;
import pers.eastwind.billmanager.user.repository.UserRepository;

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
     * 获取启用用户
     */
    public PageResult<UserDTO> getAll(QueryParam queryParam) {
        User curUser = AuthUtil.getCurUser();
        if (curUser == null || queryParam == null) {
            return PageResult.empty();
        }

        if (AuthUtil.hasAnyRole(curUser, AuthorityRole.ROLE_ADMIN)) {
            Page<User> users = userRepository.findByIsEnabled(true, queryParam.getPageable());
            return PageResult.fromPage(users, userMapper::toDTO);
        } else { // 非管理员只能获取自己
            User user = userRepository.findById(curUser.getId()).orElse(null);
            if (user == null) {
                return PageResult.empty();
            }

            return new PageResult<>(userMapper.toBaseDTOs(List.of(user)), 1L, 1, 1, 0);
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
            throw new BizException("用户名不能为空");
        }
        if (userRepository.existsUserByUsername(user.getUsername())) {
            throw new BizException("用户名已存在");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new BizException("密码不能为空");
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
    @CacheEvict(value = "user", key = "#user.username")
    public UserDTO update(UserDTO user) {
        if (user.getId() == null) {
            throw new BizException("id 不能为空");
        }
        User curUser = AuthUtil.getCurUser();
        if (curUser == null) {
            throw new AccessDeniedException("未登录");
        }
        if (curUser.getAuthority() != AuthorityRole.ROLE_ADMIN && !curUser.getId().equals(user.getId())) {
            throw new AccessDeniedException("无权限修改其他用户信息");
        }

        User oldUser = userRepository.findById(user.getId()).orElse(null);
        if (oldUser == null) {
            throw new BizException("用户不存在");
        }
        if (!oldUser.getUsername().equals(user.getUsername())) {
            throw new BizException("用户名不能修改");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new BizException("密码不能为空");
        }
        oldUser.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        userMapper.updateEntityFromDTO(user, oldUser);
        return userMapper.toDTO(userRepository.save(oldUser));
    }

    /**
     * 禁用用户
     *
     * @param username 用户名
     */
    @Transactional
    @CacheEvict(value = "user", key = "#username")
    public void disable(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setEnabled(false);
        userRepository.save(user);
    }

    @Override
    @Cacheable(value = "user", key = "#username")
    public @NonNull User loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        if (username.isEmpty()) {
            throw new UsernameNotFoundException("用户名不能为空");
        }
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
     * @return 登录结果
     */
    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user == null || !user.isEnabled()) {
            throw new BizException("用户不存在");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new BizException("密码不能为空");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw new BizException("用户已禁用");
        }
        String subject = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest().getHeader(HttpHeaders.HOST);
        String token = jwtUtil.generateToken(username, subject);
        return new LoginResult(token, userMapper.toDTO(user));
    }

}
