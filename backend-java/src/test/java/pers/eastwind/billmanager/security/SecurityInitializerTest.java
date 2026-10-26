package pers.eastwind.billmanager.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.security.model.PermissionEntity;
import pers.eastwind.billmanager.security.model.Role;
import pers.eastwind.billmanager.security.model.SecurityPermission;
import pers.eastwind.billmanager.security.repository.PermissionRepository;
import pers.eastwind.billmanager.security.repository.RolePermissionRepository;
import pers.eastwind.billmanager.security.repository.RoleRepository;
import pers.eastwind.billmanager.security.service.SecurityInitializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityInitializerTest extends BaseServiceTest {
    @Autowired
    private SecurityInitializer securityInitializer;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    void shouldCreateDefaultRolesAndGrantPermissionConfig() {
        Role existingAdmin = roleRepository.findByCode("ADMIN");
        if (existingAdmin != null) {
            rolePermissionRepository.deleteByRoleId(existingAdmin.getId());
            rolePermissionRepository.flush();
            roleRepository.delete(existingAdmin);
            roleRepository.flush();
        }
        Role existingUser = roleRepository.findByCode("USER");
        if (existingUser != null) {
            rolePermissionRepository.deleteByRoleId(existingUser.getId());
            rolePermissionRepository.flush();
            roleRepository.delete(existingUser);
            roleRepository.flush();
        }

        securityInitializer.run(null);

        Role admin = roleRepository.findByCode("ADMIN");
        assertNotNull(admin);
        assertEquals("管理员", admin.getName());
        Role user = roleRepository.findByCode("USER");
        assertNotNull(user);
        assertEquals("普通用户", user.getName());

        List<PermissionEntity> permissions = permissionRepository.findByRoleIds(List.of(admin.getId()));
        List<String> codes = permissions.stream().map(PermissionEntity::getCode).toList();
        assertTrue(codes.contains(SecurityPermission.QUERY.getCode()));
        assertTrue(codes.contains(SecurityPermission.UPDATE.getCode()));
    }
}
