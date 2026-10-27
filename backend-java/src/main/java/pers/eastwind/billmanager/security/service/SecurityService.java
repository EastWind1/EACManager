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
import java.util.function.Function;
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

    public List<RoleDTO> getRoles() {
        return roleMapper.toBaseDTOs(roleRepository.findAllByOrderBySortOrderAscIdAsc());
    }

    public List<RoleDTO> getRolesByUserId(Integer id) {
        return roleMapper.toBaseDTOs(roleRepository.findEnabledByUserId(id));
    }

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
     * 修改角色；编码不可修改
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
        // 两张关联表都是 JPQL 批量删除，不会先逐条查出来
        userRoleRepository.deleteByRoleId(id);
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(role);
    }

    // -----------------------------------------------------------------------
    // 权限目录
    // -----------------------------------------------------------------------

    public Map<String, List<PermissionDTO>> getPermissions() {
        List<PermissionEntity> permissions = permissionRepository.findAllByOrderBySortOrderAsc();
        Map<String, List<PermissionDTO>> res = new HashMap<>();
        for (PermissionEntity permission : permissions) {
            res.computeIfAbsent(permission.getModule(), _ -> new ArrayList<>()).add(permissionMapper.toBaseDTO(permission));
        }
        return res;
    }

    /**
     * 是否存在拥有启用状态 ADMIN 角色的用户
     */
    public boolean hasAdminUser() {
        return userRoleRepository.countByRoleCode(ADMIN_ROLE_CODE) > 0;
    }

    /**
     * 单个用户的角色与权限；列表场景请用批量版本，避免 N+1
     */
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
     * 批量组装用户权限
     *
     * <p>用户列表页逐用户调用 {@link #getUserAuthorities(Integer)} 会退化成 N+1（每个用户两次查询）；
     * 这里固定 3 次查询：用户角色关联、启用角色、角色权限对，与用户数无关。</p>
     *
     * @param userIds 用户 ID
     * @return 用户 ID → 角色与权限
     */
    public Map<Integer, AuthConfig> getUserAuthorities(Collection<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRole> relations = userRoleRepository.findByUserIdIn(userIds);
        Set<Integer> roleIds = relations.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, Role> rolesById = roleIds.isEmpty()
                ? Map.of()
                : roleRepository.findByIdIn(roleIds).stream()
                .filter(role -> !role.isDisabled())
                .collect(Collectors.toMap(Role::getId, Function.identity()));

        Map<Integer, List<PermissionEntity>> permissionsByRole = new HashMap<>();
        if (!roleIds.isEmpty()) {
            for (RolePermissionRow row : roleRepository.findPermissionsByRoleIds(roleIds)) {
                permissionsByRole.computeIfAbsent(row.roleId(), _ -> new ArrayList<>()).add(row.permission());
            }
        }

        Map<Integer, List<Integer>> roleIdsByUser = new HashMap<>();
        for (UserRole relation : relations) {
            if (rolesById.containsKey(relation.getRoleId())) {
                roleIdsByUser.computeIfAbsent(relation.getUserId(), _ -> new ArrayList<>()).add(relation.getRoleId());
            }
        }

        Map<Integer, AuthConfig> result = new HashMap<>();
        for (Integer userId : userIds) {
            List<Role> roles = roleIdsByUser.getOrDefault(userId, List.of()).stream()
                    .map(rolesById::get)
                    .sorted(Comparator.comparing(Role::getSortOrder).thenComparing(Role::getId))
                    .toList();
            List<RoleDTO> roleDTOs = new ArrayList<>(roles.size());
            List<PermissionEntity> permissions = new ArrayList<>();
            Set<Integer> permissionIds = new LinkedHashSet<>();
            for (Role role : roles) {
                roleDTOs.add(roleMapper.toBaseDTO(role));
                for (PermissionEntity permission : permissionsByRole.getOrDefault(role.getId(), List.of())) {
                    if (permissionIds.add(permission.getId())) {
                        permissions.add(permission);
                    }
                }
            }
            permissions.sort(Comparator.comparing(PermissionEntity::getSortOrder)
                    .thenComparing(PermissionEntity::getCode));
            result.put(userId, new AuthConfig(roleDTOs, permissionMapper.toBaseDTOs(permissions)));
        }
        return result;
    }

    /**
     * 整体替换用户角色：先清空现有关系再写入，未知角色编码直接报错
     */
    @Transactional
    public void setUserRoles(Integer userId, Set<String> roleCodes) {
        userRoleRepository.deleteByUserId(userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        List<Role> roles = roleRepository.findByCodeIn(roleCodes);
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

    public List<PermissionDTO> getRolePermissions(Integer roleId) {
        roleRepository.findById(roleId).orElseThrow(() -> new BizException("角色不存在"));
        return permissionMapper.toBaseDTOs(permissionRepository.findByRoleIds(List.of(roleId)));
    }

    /**
     * 整体替换角色权限；ADMIN 必须保留 permission:query / permission:update，否则拒绝
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

        List<RolePermission> entities = requested.stream().map(id -> {
            RolePermission entity = new RolePermission();
            entity.setRoleId(roleId);
            entity.setPermissionId(id);
            return entity;
        }).toList();
        if (!entities.isEmpty()) {
            rolePermissionRepository.saveAll(entities);
        }

        // 一次查询即可同时完成「管理员保留权限校验」与返回值组装
        List<PermissionEntity> granted = permissionRepository.findByRoleIds(List.of(roleId));
        if (ADMIN_ROLE_CODE.equals(targetRole.getCode())) {
            Set<String> codes = granted.stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
            if (!codes.contains(SecurityPermission.QUERY.getCode())
                    || !codes.contains(SecurityPermission.UPDATE.getCode())) {
                throw new BizException("管理员角色必须保留权限配置的查看和修改权限");
            }
        }

        return permissionMapper.toBaseDTOs(granted);
    }
}
