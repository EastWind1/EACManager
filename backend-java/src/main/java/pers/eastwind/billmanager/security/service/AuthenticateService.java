package pers.eastwind.billmanager.security.service;

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
import pers.eastwind.billmanager.security.model.*;
import pers.eastwind.billmanager.security.repository.AccountRepository;
import pers.eastwind.billmanager.security.repository.RoleRepository;
import pers.eastwind.billmanager.security.util.AuthUtil;
import pers.eastwind.billmanager.security.util.JWTUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 账户与认证
 */
@Slf4j
@Service
public class AuthenticateService implements UserDetailsService {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("[a-zA-Z0-9*\\-_^~]+");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,31}$");

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AuthorizeService authorizeService;
    private final JWTProperties properties;

    public AuthenticateService(AccountRepository accountRepository, RoleRepository roleRepository,
                          AuthorizeService authorizeService, JWTProperties properties) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.authorizeService = authorizeService;
        this.properties = properties;
    }

    public boolean isInitializationRequired() {
        return !authorizeService.hasAdminUser();
    }

    public LoginResult login(String username, String password) {
        Account account = accountRepository.findByUsername(username);
        if (account == null) {
            throw new BizException("用户名或密码错误");
        }
        if (account.isDisabled()) {
            throw new BizException("用户已禁用");
        }
        if (account.getPassword() == null || account.getPassword().isEmpty()
                || !BCrypt.checkpw(password, account.getPassword())) {
            log.warn("登录失败: {}", username);
            throw new BizException("用户名或密码错误");
        }
        String token = JWTUtil.generateToken(properties.getSecret(), properties.getExpire(), username);
        log.info("用户登录: {}", username);
        return new LoginResult(token, toDTO(account));
    }

    @Transactional
    public AuthorityUser initialize(String username, String password) {
        Role adminRole = roleRepository.findByCodeForUpdate(AuthorizeService.ADMIN_ROLE_CODE);
        if (adminRole == null) {
            throw new BizException("管理员角色不存在，无法初始化");
        }
        if (authorizeService.hasAdminUser()) {
            throw new BizException("系统已初始化，不能重复创建管理员");
        }
        validUsername(username);
        validPassword(password);
        Account account = new Account();
        account.setUsername(username.trim());
        account.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        account.setDisabled(false);
        Account saved = accountRepository.save(account);
        authorizeService.setUserRoles(saved.getId(), Set.of(AuthorizeService.ADMIN_ROLE_CODE));
        log.info("系统初始化管理员: {}", username);
        return toDTO(saved);
    }

    /**
     * 用户模块创建资料前先创建账户；角色授权与管理员保护在本模块完成。
     */
    @Transactional
    public AuthorityUser createAccount(String username, String password, Collection<RoleDTO> roles) {
        validUsername(username);
        if (accountRepository.findByUsername(username.trim()) != null) {
            throw new BizException("用户名已存在");
        }
        validPassword(password);

        Set<String> roleCodes = new HashSet<>();
        if (roles == null || roles.isEmpty()) {
            roleCodes.add("USER");
        } else {
            roles.forEach(role -> roleCodes.add(role.getCode()));
        }
        assertCanAssignRoles(roleCodes);

        Account account = new Account();
        account.setUsername(username.trim());
        account.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        account.setDisabled(false);
        Account saved = accountRepository.save(account);
        authorizeService.setUserRoles(saved.getId(), roleCodes);
        return toDTO(saved);
    }

    /**
     * 更新账户角色和密码；用户资料由 user 模块单独维护。
     */
    @Transactional
    public AuthorityUser updateAccount(Integer id, Collection<RoleDTO> roles, String password) {
        if (id == null) {
            throw new BizException("id 不能为空");
        }
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null) {
            throw new AccessDeniedException("未登录");
        }
        boolean isAdmin = AuthUtil.hasRole(AuthorizeService.ADMIN_ROLE_CODE);
        if (!isAdmin && !curUser.getId().equals(id)) {
            throw new AccessDeniedException("无权限修改其他用户信息");
        }
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            throw new BizException("用户不存在");
        }
        if (roles != null) {
            Set<String> currentRoles = authorizeService.getRolesByUserId(id).stream()
                    .map(RoleDTO::getCode)
                    .collect(Collectors.toSet());
            Set<String> roleCodes = roles.stream().map(RoleDTO::getCode).collect(Collectors.toSet());
            if (!isAdmin && !roleCodes.equals(currentRoles)) {
                throw new BizException("无权更改角色");
            }
            if (isAdmin) {
                if (currentRoles.contains(AuthorizeService.ADMIN_ROLE_CODE)
                        && !roleCodes.contains(AuthorizeService.ADMIN_ROLE_CODE)
                        && authorizeService.countEnabledUsersByRoleCode(AuthorizeService.ADMIN_ROLE_CODE) <= 1) {
                    throw new BizException("必须保留至少一个启用的管理员");
                }
                authorizeService.setUserRoles(id, roleCodes);
            }
        }
        if (password != null && !password.isEmpty()) {
            validPassword(password);
            account.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
            accountRepository.save(account);
        }
        return toDTO(account);
    }

    @Transactional
    public void disable(String username) {
        Account account = accountRepository.findByUsernameAndDisabled(username, false);
        if (account == null) {
            throw new BizException("用户不存在");
        }
        disable(account.getId());
    }

    @Transactional
    public void disable(Integer id) {
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            throw new BizException("用户不存在");
        }
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null) {
            throw new AccessDeniedException("未登录");
        }
        if (curUser.getId().equals(account.getId())) {
            throw new BizException("不能禁用自己");
        }
        if (authorizeService.userHasRole(account.getId(), AuthorizeService.ADMIN_ROLE_CODE)
                && authorizeService.countEnabledUsersByRoleCode(AuthorizeService.ADMIN_ROLE_CODE) <= 1) {
            throw new BizException("必须保留至少一个启用的管理员");
        }
        account.setDisabled(true);
        accountRepository.save(account);
    }

    @Override
    public @NonNull AuthorityUser loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsernameAndDisabled(username, false);
        if (account == null) {
            throw new UsernameNotFoundException("未找到用户名为 " + username + " 的用户");
        }
        AuthConfig config = authorizeService.getUserAuthorities(account.getId());
        return toDTO(account, config);
    }

    public AuthorityUser findById(Integer id) {
        if (id == null) {
            return null;
        }
        return accountRepository.findById(id).map(this::toDTO).orElse(null);
    }

    public AuthorityUser findEnabledById(Integer id) {
        if (id == null) {
            return null;
        }
        return accountRepository.findByIdAndDisabledFalse(id).map(this::toBaseDTO).orElse(null);
    }

    public List<AuthorityUser> findByIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return accountRepository.findByIdIn(ids).stream().map(this::toBaseDTO).toList();
    }

    public List<AuthorityUser> findEnabledUsers() {
        return accountRepository.findByDisabledFalse().stream().map(this::toBaseDTO).toList();
    }

    public PageResult<AuthorityUser> findEnabledPage(QueryParam queryParam) {
        Page<Account> page = accountRepository.findByDisabled(false, queryParam.getPageable());
        Map<Integer, AuthConfig> authorities = authorizeService.getUserAuthorities(
                page.getContent().stream().map(Account::getId).toList());
        return PageResult.fromPage(page, account ->
                toDTO(account, authorities.getOrDefault(account.getId(), new AuthConfig(List.of(), List.of()))));
    }

    public List<AuthorityUser> findEnabledUsersByRole(String roleCode) {
        return accountRepository.findEnabledByRoleCode(roleCode).stream().map(this::toDTO).toList();
    }

    private AuthorityUser toDTO(Account account) {
        return toDTO(account, authorizeService.getUserAuthorities(account.getId()));
    }

    /**
     * 返回不包含权限的基础 DTO
     */
    private AuthorityUser toBaseDTO(Account account) {
        AuthorityUser dto = new AuthorityUser();
        dto.setId(account.getId());
        dto.setUsername(account.getUsername());
        dto.setDisabled(account.isDisabled());
        return dto;
    }

    private AuthorityUser toDTO(Account account, AuthConfig config) {
        AuthorityUser dto = new AuthorityUser();
        dto.setId(account.getId());
        dto.setUsername(account.getUsername());
        dto.setPassword(account.getPassword());
        dto.setDisabled(account.isDisabled());
        dto.setRoles(config.roles());
        dto.setPermissions(config.permissions());
        return dto;
    }

    private void assertCanAssignRoles(Set<String> roleCodes) {
        if (AuthUtil.hasRole(AuthorizeService.ADMIN_ROLE_CODE)) {
            return;
        }
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null) {
            throw new AccessDeniedException("未登录");
        }
        Set<String> ownRoles = authorizeService.getRolesByUserId(curUser.getId()).stream()
                .map(RoleDTO::getCode)
                .collect(Collectors.toSet());
        if (!ownRoles.containsAll(roleCodes)) {
            throw new BizException("无权授予未持有的角色");
        }
    }

    private void validUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new BizException("用户名必须为 4-32 位，以字母开头，仅包含字母、数字和下划线");
        }
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
        int classes = 0;
        boolean upper = false, lower = false, digit = false, other = false;
        for (char c : password.toCharArray()) {
            if ('a' <= c && c <= 'z') lower = true;
            else if ('A' <= c && c <= 'Z') upper = true;
            else if ('0' <= c && c <= '9') digit = true;
            else other = true;
        }
        classes = (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (other ? 1 : 0);
        if (classes < 2) {
            throw new BizException("密码必须包含大写、小写字母、数字以及 *-_^~ 这四种符号的至少两种");
        }
    }
}
