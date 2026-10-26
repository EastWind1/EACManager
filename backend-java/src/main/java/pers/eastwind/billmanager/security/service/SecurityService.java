package pers.eastwind.billmanager.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.security.model.*;
import pers.eastwind.billmanager.security.repository.PermissionRepository;
import pers.eastwind.billmanager.security.repository.RolePermissionRepository;
import pers.eastwind.billmanager.security.repository.RoleRepository;
import pers.eastwind.billmanager.security.repository.UserRoleRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色、权限与用户角色组装服务
 */
@Slf4j
@Service
public class SecurityService {
    public static final String ADMIN_ROLE_CODE = "ADMIN";

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public SecurityService(RoleMapper roleMapper, PermissionMapper permissionMapper, RoleRepository roleRepository,
                           UserRoleRepository userRoleRepository,
                           RolePermissionRepository rolePermissionRepository,
                           PermissionRepository permissionRepository) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    // -----------------------------------------------------------------------
    // 角色管理
    // -----------------------------------------------------------------------

    /**
     * 查询全部角色
     */
    public List<RoleDTO> getRoles() {
        return roleMapper.toBaseDTOs(roleRepository.findAllByOrderBySortOrderAscIdAsc());
    }

    /**
     * 查询用户拥有的角色
     */
    public List<RoleDTO> getRolesByUserId(Integer id) {
        return roleMapper.toBaseDTOs(roleRepository.findEnabledByUserId(id));
    }

    /**
     * 创建角色
     */
    @Transactional
    public RoleDTO createRole(RoleDTO dto) {
        if (dto.getCode() == null || !dto.getCode().matches("^[A-Z][A-Z0-9_]*$")) {
            throw new BizException("角色编码必须为大写字母、数字或下划线，且不能以数字开头");
        }
        if (dto.getCode().startsWith("ROLE_")) {
            throw new BizException("角色编码不需要 ROLE_ 前缀");
        }
        if (roleRepository.existsByCode(dto.getCode())) {
            throw new BizException("角色编码已存在");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException("角色名称不能为空");
        }
        Role role = new Role();
        role.setCode(dto.getCode());
        role.setName(dto.getName());
        role.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        role.setDisabled(false);
        return roleMapper.toBaseDTO(roleRepository.save(role));
    }

    /**
     * 修改角色
     * <p>
     * 编码不可修改
     */
    @Transactional
    public RoleDTO updateRole(RoleDTO dto) {
        if (dto.getId() == null) {
            throw new BizException("角色 id 不能为空");
        }
        Role role = roleRepository.findById(dto.getId())
                .orElseThrow(() -> new BizException("角色不存在"));
        if (ADMIN_ROLE_CODE.equals(role.getCode()) && dto.isDisabled()) {
            throw new BizException("管理员角色不能禁用");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException("角色名称不能为空");
        }
        role.setName(dto.getName());
        role.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        role.setDisabled(dto.isDisabled());
        return roleMapper.toBaseDTO(roleRepository.save(role));
    }

    /**
     * 删除角色；管理员角色不允许删除
     */
    @Transactional
    public void deleteRole(Integer id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new BizException("角色不存在"));
        if (ADMIN_ROLE_CODE.equals(role.getCode())) {
            throw new BizException("管理员角色不能删除");
        }
        userRoleRepository.deleteByRoleId(id);
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(role);
    }

    // -----------------------------------------------------------------------
    // 权限目录
    // -----------------------------------------------------------------------

    /**
     * 获取全部可配置权限
     */
    public Map<String, List<PermissionDTO>> getPermissions() {
        List<PermissionEntity> permissions = permissionRepository.findAllByOrderBySortOrderAsc();
        Map<String, List<PermissionDTO>> res = new HashMap<>();
        for (PermissionEntity permission : permissions) {
            res.computeIfAbsent(permission.getModule(), _ -> new ArrayList<>()).add(permissionMapper.toBaseDTO(permission));
        }
        return res;
    }

    /**
     * 是否已经存在管理员用户
     */
    public boolean hasAdminUser() {
        return userRoleRepository.countByRoleCode(ADMIN_ROLE_CODE) > 0;
    }

    public AuthConfig getUserAuthorities(Integer userId) {
        if (userId == null) {
            return new AuthConfig(List.of(), List.of());
        }

        List<Role> roles = roleRepository.findEnabledByUserId(userId);
        List<RoleDTO> roleDTOs = new ArrayList<>(roles.size());
        List<Integer> roleIds = new ArrayList<>(roles.size());
        for (Role role : roles) {
            roleDTOs.add(roleMapper.toBaseDTO(role));
            roleIds.add(role.getId());
        }
        List<PermissionEntity> permissions = permissionRepository.findByRoleIds(roleIds);
        List<PermissionDTO> permissionDTOs = new ArrayList<>(permissions.size());
        for (PermissionEntity p : permissions) {
            permissionDTOs.add(permissionMapper.toBaseDTO(p));
        }

        return new AuthConfig(roleDTOs, permissionDTOs);
    }

    /**
     * 设置用户角色
     */
    @Transactional
    public void setUserRoles(Integer userId, Set<String> roleCodes) {
        userRoleRepository.deleteByUserId(userId);
        userRoleRepository.flush();
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        List<Role> roles = roleRepository.findAll().stream()
                .filter(role -> roleCodes.contains(role.getCode()))
                .toList();
        if (roles.size() != roleCodes.size()) {
            throw new BizException("存在未知角色");
        }
        List<UserRole> relations = roles.stream().map(role -> {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            return relation;
        }).toList();
        userRoleRepository.saveAll(relations);
    }

    // -----------------------------------------------------------------------
    // 权限配置
    // -----------------------------------------------------------------------

    /**
     * 查询角色权限
     */
    public List<PermissionDTO> getRolePermissions(Integer roleId) {
        roleRepository.findById(roleId).orElseThrow(() -> new BizException("角色不存在"));
        return permissionMapper.toBaseDTOs(permissionRepository.findByRoleIds(List.of(roleId)));
    }

    /**
     * 更新角色权限
     */
    @Transactional
    public List<PermissionDTO> updateRolePermissions(Integer roleId, Set<Integer> permissionIds) {
        if (roleId == null) {
            throw new BizException("角色为空");
        }
        Role targetRole = roleRepository.findById(roleId).orElseThrow(() -> new BizException("角色不存在"));

        Set<Integer> requested = permissionIds == null ? Set.of() : permissionIds;
        Set<Integer> existing = permissionRepository.findByIdIn(requested).stream()
                .map(PermissionEntity::getId)
                .collect(Collectors.toSet());
        if (!existing.containsAll(requested)) {
            Set<Integer> missing = new HashSet<>(requested);
            missing.removeAll(existing);
            throw new BizException("未知权限: " + missing);
        }

        rolePermissionRepository.deleteByRoleId(roleId);
        rolePermissionRepository.flush();

        List<RolePermission> entities = requested.stream().map(id -> {
            RolePermission entity = new RolePermission();
            entity.setRoleId(roleId);
            entity.setPermissionId(id);
            return entity;
        }).toList();
        rolePermissionRepository.saveAll(entities);
        rolePermissionRepository.flush();

        if (ADMIN_ROLE_CODE.equals(targetRole.getCode())) {
            Set<String> codes = permissionRepository.findByRoleIds(List.of(roleId)).stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
            if (!codes.contains(SecurityPermission.QUERY.getCode())
                    || !codes.contains(SecurityPermission.UPDATE.getCode())) {
                throw new BizException("管理员角色必须保留权限配置的查看和修改权限");
            }
        }

        return getRolePermissions(roleId);
    }
}
