package pers.eastwind.billmanager.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.RoleDTO;
import pers.eastwind.billmanager.security.model.SecurityPermission;
import pers.eastwind.billmanager.security.service.SecurityInitializer;
import pers.eastwind.billmanager.security.service.SecurityService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityInitializerTest extends BaseServiceTest {
    @Autowired
    private SecurityInitializer securityInitializer;
    @Autowired
    private SecurityService securityService;

    @Test
    void shouldCreateDefaultRolesAndGrantPermissionConfig() {
        securityInitializer.run(null);

        RoleDTO admin = securityService.getRoles().stream()
                .filter(role -> "ADMIN".equals(role.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(admin);
        RoleDTO user = securityService.getRoles().stream()
                .filter(role -> "USER".equals(role.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(user);

        List<String> codes = securityService.getRolePermissions(admin.getId()).stream()
                .map(PermissionDTO::getCode)
                .toList();
        assertTrue(codes.contains(SecurityPermission.QUERY.getCode()));
        assertTrue(codes.contains(SecurityPermission.UPDATE.getCode()));
    }
}
