package pers.eastwind.billmanager.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.security.model.PermissionEntity;
import pers.eastwind.billmanager.security.model.Role;
import pers.eastwind.billmanager.security.model.RolePermission;
import pers.eastwind.billmanager.security.model.SecurityPermission;
import pers.eastwind.billmanager.security.repository.PermissionRepository;
import pers.eastwind.billmanager.security.repository.RolePermissionRepository;
import pers.eastwind.billmanager.security.repository.RoleRepository;

/**
 * 安全模块初始化。
 * <p>
 * 权限类型同步完成后执行：确保管理员、普通用户角色存在，
 * 并补齐管理员角色的权限配置查看和修改权限。
 */
@Slf4j
@Component
@Order(1)
public class SecurityInitializer implements ApplicationRunner {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public SecurityInitializer(RoleRepository roleRepository,
                               PermissionRepository permissionRepository,
                               RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role admin = ensureRole(SecurityService.ADMIN_ROLE_CODE, "管理员", 1);
        ensureRole("USER", "普通用户", 2);

        if (!rolePermissionRepository.existsByRoleCodeAndPermissionCode(SecurityService.ADMIN_ROLE_CODE, SecurityPermission.QUERY.getCode())) {
            RolePermission relation = new RolePermission();
            PermissionEntity query = permissionRepository.findByCode(SecurityPermission.QUERY.getCode());
            if (query == null) {
                throw new IllegalStateException("管理员初始化失败：缺少权限配置所需权限");
            }
            relation.setRoleId(admin.getId());
            relation.setPermissionId(query.getId());
            rolePermissionRepository.save(relation);
        }

        if (!rolePermissionRepository.existsByRoleCodeAndPermissionCode(SecurityService.ADMIN_ROLE_CODE, SecurityPermission.UPDATE.getCode())) {
            RolePermission relation = new RolePermission();
            PermissionEntity query = permissionRepository.findByCode(SecurityPermission.UPDATE.getCode());
            if (query == null) {
                throw new IllegalStateException("管理员初始化失败：缺少权限配置所需权限");
            }
            relation.setRoleId(admin.getId());
            relation.setPermissionId(query.getId());
            rolePermissionRepository.save(relation);
        }
        log.info("管理员角色权限不完整，已创建并授予权限配置权限");
    }

    private Role ensureRole(String code, String name, int sortOrder) {
        Role role = roleRepository.findByCode(code);
        if (role != null) {
            return role;
        }
        role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setSortOrder(sortOrder);
        role.setDisabled(false);
        return roleRepository.save(role);
    }
}
