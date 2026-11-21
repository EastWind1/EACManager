package pers.eastwind.billmanager.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.service.AuthenticateService;
import pers.eastwind.billmanager.security.service.AuthorizeService;
import pers.eastwind.billmanager.security.util.AuthUtil;
import pers.eastwind.billmanager.user.model.LoginResult;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.model.UserProfile;
import pers.eastwind.billmanager.user.repository.UserProfileRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户资料服务：只维护姓名、手机、邮箱等非鉴权信息；账户创建、登录、禁用和角色由安全模块负责。
 */
@Service
public class UserService {
    private final UserProfileRepository profileRepository;
    private final AuthenticateService authenticateService;

    public UserService(UserProfileRepository profileRepository, AuthenticateService authenticateService) {
        this.profileRepository = profileRepository;
        this.authenticateService = authenticateService;
    }

    public PageResult<UserDTO> getAll(QueryParam queryParam) {
        AuthorityUser curUser = AuthUtil.getCurUser();
        if (curUser == null || queryParam == null) {
            return PageResult.empty();
        }
        if (AuthUtil.hasRole(AuthorizeService.ADMIN_ROLE_CODE)) {
            PageResult<AuthorityUser> page = authenticateService.findEnabledPage(queryParam);
            Map<Integer, UserProfile> profiles = profileMap(page.items().stream().map(AuthorityUser::getId).toList());
            List<UserDTO> dtos = page.items().stream()
                    .map(account -> toDTO(account, profiles.get(account.getId())))
                    .toList();
            return new PageResult<>(dtos, page.totalCount(), page.totalPages(), page.pageSize(), page.pageIndex());
        }
        AuthorityUser account = authenticateService.findById(curUser.getId());
        if (account == null) {
            return PageResult.empty();
        }
        UserProfile profile = profileRepository.findById(account.getId()).orElse(null);
        return new PageResult<>(List.of(toDTO(account, profile)), 1L, 1, 1, 0);
    }

    @Transactional
    public UserDTO create(UserDTO user) {
        if (user == null) {
            throw new BizException("用户不能为空");
        }
        AuthorityUser account = authenticateService.createAccount(user.getUsername(), user.getPassword(), user.getRoles());
        UserProfile profile = new UserProfile();
        profile.setUserId(account.getId());
        profile.setName(user.getName().trim());
        profile.setPhone(user.getPhone());
        profile.setEmail(user.getEmail());
        profileRepository.save(profile);
        return toDTO(account, profile);
    }

    @Transactional
    public UserDTO update(UserDTO user) {
        if (user == null || user.getId() == null) {
            throw new BizException("id 不能为空");
        }
        AuthorityUser existing = authenticateService.findById(user.getId());
        if (existing == null) {
            throw new BizException("用户不存在");
        }
        if (!Objects.equals(existing.getUsername(), user.getUsername())) {
            throw new BizException("用户名不能修改");
        }
        AuthorityUser account = authenticateService.updateAccount(user.getId(), user.getRoles(), user.getPassword());
        UserProfile profile = profileRepository.findById(user.getId()).orElseGet(() -> {
            UserProfile created = new UserProfile();
            created.setUserId(user.getId());
            return created;
        });
        profile.setName(user.getName().trim());
        profile.setPhone(user.getPhone());
        profile.setEmail(user.getEmail());
        profileRepository.save(profile);
        return toDTO(account, profile);
    }

    public LoginResult login(String username, String password) {
        pers.eastwind.billmanager.security.model.LoginResult result = authenticateService.login(username, password);
        UserProfile profile = profileRepository.findById(result.account().getId()).orElse(null);
        return new LoginResult(result.token(), toDTO(result.account(), profile));
    }

    public UserDTO initialize(UserDTO dto) {
        if (dto == null) {
            throw new BizException("初始化信息不能为空");
        }
        AuthorityUser account = authenticateService.initialize(dto.getUsername(), dto.getPassword());
        UserProfile profile = new UserProfile();
        profile.setUserId(account.getId());
        profile.setName(dto.getName().trim());
        profile.setPhone(dto.getPhone());
        profile.setEmail(dto.getEmail());
        profileRepository.save(profile);
        return toDTO(account, profile);
    }

    public boolean isInitializationRequired() {
        return authenticateService.isInitializationRequired();
    }

    public List<UserDTO> findEnabledUsersByRole(String roleCode) {
        return withProfiles(authenticateService.findEnabledUsersByRole(roleCode));
    }

    public List<UserDTO> findEnabledUsers() {
        return withProfiles(authenticateService.findEnabledUsers());
    }

    public List<UserDTO> findByIds(Collection<Integer> ids) {
        return withProfiles(authenticateService.findByIds(ids));
    }

    public UserDTO findEnabledById(Integer id) {
        AuthorityUser account = authenticateService.findEnabledById(id);
        if (account == null) {
            return null;
        }
        return toDTO(account, profileRepository.findById(id).orElse(null));
    }

    private List<UserDTO> withProfiles(List<AuthorityUser> accounts) {
        Map<Integer, UserProfile> profiles = profileMap(accounts.stream().map(AuthorityUser::getId).toList());
        return accounts.stream()
                .map(account -> toDTO(account, profiles.get(account.getId())))
                .toList();
    }

    private Map<Integer, UserProfile> profileMap(Collection<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return profileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
    }

    private UserDTO toDTO(AuthorityUser account, UserProfile profile) {
        UserDTO dto = new UserDTO();
        dto.setId(account.getId());
        dto.setUsername(account.getUsername());
        dto.setDisabled(account.isDisabled());
        dto.setRoles(account.getRoles());
        dto.setPermissions(account.getPermissions());
        if (profile != null) {
            dto.setName(profile.getName());
            dto.setPhone(profile.getPhone());
            dto.setEmail(profile.getEmail());
        }
        return dto;
    }


}
