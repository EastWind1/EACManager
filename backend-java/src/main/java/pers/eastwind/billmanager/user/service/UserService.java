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
import pers.eastwind.billmanager.security.model.AuthConfig;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService implements UserDetailsService {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("[a-zA-Z0-9*\\-_^~]+");

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

    public PageResult<UserDTO> getAll(QueryParam queryParam) {
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null || queryParam == null) {
            return PageResult.empty();
        }

        if (AuthUtil.hasRole("ROLE_ADMIN")) {
            Page<User> users = userRepository.findByDisabled(false, queryParam.getPageable());
            // 一次性组装整页用户的角色与权限，避免每用户两次查询
            Map<Integer, AuthConfig> authorities = securityService.getUserAuthorities(
                    users.getContent().stream().map(User::getId).toList());
            return PageResult.fromPage(users, user -> {
                AuthConfig config = authorities.getOrDefault(user.getId(), new AuthConfig(List.of(), List.of()));
                return userMapper.toDTO(user, config.roles(), config.permissions());
            });
        }

        // 非管理员只能获取自己
        User user = userRepository.findById(curUser.getId()).orElse(null);
        if (user == null) {
            return PageResult.empty();
        }
        return new PageResult<>(List.of(toDTO(user)), 1L, 1, 1, 0);
    }

    private void validPassword(String password) {
        if (password == null || password.length() < 10) {
            throw new BizException("密码过短");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BizException("密码不能超过 72 字节");
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

    @Transactional
    public UserDTO create(UserDTO user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
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
        assertCanAssignRoles(roleCodes);

        User newUser = userMapper.toEntity(user);
        newUser.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        User saved = userRepository.save(newUser);
        securityService.setUserRoles(saved.getId(), roleCodes);
        return toDTO(saved);
    }

    /**
     * 非管理员只能授予自己已持有的角色
     */
    private void assertCanAssignRoles(Set<String> roleCodes) {
        if (AuthUtil.hasRole("ROLE_ADMIN")) {
            return;
        }
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null) {
            throw new AccessDeniedException("未登录");
        }
        Set<String> ownRoles = securityService.getRolesByUserId(curUser.getId()).stream()
                .map(RoleDTO::getCode)
                .collect(Collectors.toSet());
        if (!ownRoles.containsAll(roleCodes)) {
            throw new BizException("无权授予未持有的角色");
        }
    }

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
                if (currentRoles.contains(SecurityService.ADMIN_ROLE_CODE)
                        && !roleCodes.contains(SecurityService.ADMIN_ROLE_CODE)
                        && securityService.countEnabledUsersByRoleCode(SecurityService.ADMIN_ROLE_CODE) <= 1) {
                    throw new BizException("必须保留至少一个启用的管理员");
                }
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

    @Transactional
    public void disable(String username) {
        User user = userRepository.findByUsernameAndDisabled(username, false);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null) {
            throw new AccessDeniedException("未登录");
        }
        if (curUser.getId().equals(user.getId())) {
            throw new BizException("不能禁用自己");
        }
        if (securityService.userHasRole(user.getId(), SecurityService.ADMIN_ROLE_CODE)
                && securityService.countEnabledUsersByRoleCode(SecurityService.ADMIN_ROLE_CODE) <= 1) {
            throw new BizException("必须保留至少一个启用的管理员");
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

    public boolean isInitializationRequired() {
        return !securityService.hasAdminUser();
    }

    /**
     * 创建首个管理员；仅当系统没有任何管理员用户时允许执行，先锁 ADMIN 角色行以串行化并发初始化
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
        // 初始化页提交的是明文密码（登录页提交的是前端 sha256(password:username) 摘要），
        // 这里按同一约定先做摘要再 bcrypt，保证初始化后能直接登录
        user.setPassword(BCrypt.hashpw(sha256Hex(dto.getPassword() + ":" + dto.getUsername()), BCrypt.gensalt()));
        user.setName(dto.getName().trim());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setDisabled(false);
        User saved = userRepository.save(user);
        securityService.setUserRoles(saved.getId(), Set.of(SecurityService.ADMIN_ROLE_CODE));
        log.info("系统初始化管理员: {}", dto.getUsername());
        return userMapper.toDTO(saved);
    }


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
        String token = JWTUtil.generateToken(properties.getSecret(), properties.getExpire(), username);
        log.info("用户登录: {}", username);
        return new LoginResult(token, toDTO(user));
    }

    /**
     * 与前端 {@code Crypto.SHA256(password, username)}、Go 后端 {@code sha256Hex} 保持一致
     */
    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private UserDTO toDTO(User user) {
        var config = securityService.getUserAuthorities(user.getId());
        return userMapper.toDTO(user, config.roles(), config.permissions());
    }
}
