package pers.eastwind.billmanager.user.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.security.config.JWTProperties;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.model.Role;
import pers.eastwind.billmanager.security.model.RoleDTO;
import pers.eastwind.billmanager.security.repository.RoleRepository;
import pers.eastwind.billmanager.security.service.SecurityService;
import pers.eastwind.billmanager.security.util.AuthUtil;
import pers.eastwind.billmanager.security.util.JWTUtil;
import pers.eastwind.billmanager.user.model.LoginResult;
import pers.eastwind.billmanager.user.model.User;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.model.UserMapper;
import pers.eastwind.billmanager.user.repository.UserRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户服务
 */
@Slf4j
@Service
public class UserService implements UserDetailsService {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("[a-zA-Z0-9*\\-_^~]+]]");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JWTProperties properties;
    private final SecurityService securityService;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, UserMapper userMapper, JWTProperties properties,
                       SecurityService securityService, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.properties = properties;
        this.securityService = securityService;
        this.roleRepository = roleRepository;
    }

    /**
     * 获取启用用户
     */
    public PageResult<UserDTO> getAll(QueryParam queryParam) {
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null || queryParam == null) {
            return PageResult.empty();
        }

        if (AuthUtil.hasRole("ROLE_ADMIN")) {
            Page<User> users = userRepository.findByDisabled(false, queryParam.getPageable());
            return PageResult.fromPage(users, this::toDTO);
        }

        // 非管理员只能获取自己
        User user = userRepository.findById(curUser.getId()).orElse(null);
        if (user == null) {
            return PageResult.empty();
        }
        return new PageResult<>(List.of(toDTO(user)), 1L, 1, 1, 0);
    }

    /**
     * 校验密码强度
     */
    private void validPassword(String password) {
        if (password == null || password.length() < 10) {
            throw new BizException("密码过短");
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BizException("密码只能包含大写、小写字母、数字以及 *-_^~");
        }
        int uc = 0;
        int lc = 0;
        int nc = 0;
        int oc = 0;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if ('a' <= c && c <= 'z') {
                lc = 1;
            } else if ('A' <= c && c <= 'Z') {
                uc = 1;
            } else if ('0' <= c && c <= '9') {
                nc = 1;
            } else {
                oc = 1;
            }
        }
        int sum = uc + lc + nc + oc;
        if (sum < 2) {
            throw new BizException("密码必须包含大写、小写字母、数字以及 *-_^~ 这四种符号的至少两种");
        }
    }

    /**
     * 创建用户
     */
    @Transactional
    public UserDTO create(UserDTO user) {
        if (user.getUsername().isEmpty()) {
            throw new BizException("用户名不能为空");
        }
        if (userRepository.existsUserByUsername(user.getUsername())) {
            throw new BizException("用户名已存在");
        }
        validPassword(user.getPassword());

        Collection<RoleDTO> roles = user.getRoles();
        Set<String> roleCodes = new HashSet<>();
        if (roles == null || roles.isEmpty()) {
            roleCodes.add("USER");
        } else {
            for (RoleDTO role : roles) {
                roleCodes.add(role.getCode());
            }
        }
        if (roleCodes.contains("ADMIN") && !AuthUtil.hasRole("ROLE_ADMIN")) {
            throw new BizException("无权创建管理员");
        }

        User newUser = userMapper.toEntity(user);
        newUser.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        User saved = userRepository.save(newUser);
        securityService.setUserRoles(saved.getId(), roleCodes);
        return toDTO(saved);
    }

    /**
     * 更新用户
     */
    @Transactional
    public UserDTO update(UserDTO user) {
        if (user.getId() == null) {
            throw new BizException("id 不能为空");
        }
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null) {
            throw new AccessDeniedException("未登录");
        }
        boolean isAdmin = AuthUtil.hasRole("ROLE_ADMIN");
        if (!isAdmin && !curUser.getId().equals(user.getId())) {
            throw new AccessDeniedException("无权限修改其他用户信息");
        }

        User oldUser = userRepository.findById(user.getId()).orElse(null);
        if (oldUser == null) {
            throw new BizException("用户不存在");
        }
        if (!oldUser.getUsername().equals(user.getUsername())) {
            throw new BizException("用户名不能修改");
        }

        if (user.getRoles() != null) {
            Set<String> currentRoles = securityService.getRolesByUserId(user.getId()).stream()
                    .map(RoleDTO::getCode)
                    .collect(Collectors.toSet());
            Set<String> roleCodes = new HashSet<>();
            for (RoleDTO role : user.getRoles()) {
                roleCodes.add(role.getCode());
            }
            if (!isAdmin && !roleCodes.equals(currentRoles)) {
                throw new BizException("无权更改角色");
            }
            if (isAdmin) {
                securityService.setUserRoles(user.getId(), roleCodes);
            }
        }
        // 当前端不传入 password 时不更新 TODO：之后使用单独 API 实现密码更改
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            validPassword(user.getPassword());
            oldUser.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        }
        userMapper.updateEntityFromDTO(user, oldUser);
        return toDTO(userRepository.save(oldUser));
    }

    /**
     * 禁用用户
     */
    @Transactional
    public void disable(String username) {
        User user = userRepository.findByUsernameAndDisabled(username, false);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setDisabled(true);
        userRepository.save(user);
    }

    /**
     * 每次认证都重新从 security 组装用户权限
     */
    @Override
    public @NonNull UserDTO loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        if (username.isEmpty()) {
            throw new UsernameNotFoundException("用户名不能为空");
        }
        User user = userRepository.findByUsernameAndDisabled(username, false);
        if (user == null) {
            throw new UsernameNotFoundException("未找到用户名为 " + username + " 的用户");
        }
        var config = securityService.getUserAuthorities(user.getId());
        return userMapper.toDTO(user, config.roles(), config.permissions());
    }

    /**
     * 是否需要初始化管理员
     */
    public boolean isInitializationRequired() {
        return !securityService.hasAdminUser();
    }

    /**
     * 创建首个管理员；仅当系统没有任何管理员用户时允许执行
     */
    @Transactional
    public UserDTO initialize(UserDTO dto) {
        Role adminRole = roleRepository.findByCodeForUpdate(SecurityService.ADMIN_ROLE_CODE);
        if (adminRole == null) {
            throw new BizException("管理员角色不存在，无法初始化");
        }
        if (securityService.hasAdminUser()) {
            throw new BizException("系统已初始化，不能重复创建管理员");
        }

        validPassword(dto.getPassword());

        if (userRepository.existsUserByUsername(dto.getUsername())) {
            throw new BizException("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        user.setName(dto.getName().trim());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setDisabled(false);
        User saved = userRepository.save(user);
        securityService.setUserRoles(saved.getId(), Set.of(SecurityService.ADMIN_ROLE_CODE));
        log.info("系统初始化管理员: {}", dto.getUsername());
        return userMapper.toDTO(saved);
    }


    /**
     * 登录，生成 token
     */
    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        if (user.isDisabled()) {
            throw new BizException("用户已禁用");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new BizException("密码不能为空");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            log.warn("登录失败: {}", username);
            throw new BizException("用户名或密码错误");
        }
        String token = JWTUtil.generateToken(properties.getKey(), properties.getExpire(), username);
        log.info("用户登录: {}", username);
        return new LoginResult(token, toDTO(user));
    }

    private UserDTO toDTO(User user) {
        var config = securityService.getUserAuthorities(user.getId());
        return userMapper.toDTO(user, config.roles(), config.permissions());
    }
}
