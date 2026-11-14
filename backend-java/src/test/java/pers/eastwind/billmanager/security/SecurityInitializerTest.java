package pers.eastwind.billmanager.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.RoleDTO;
import pers.eastwind.billmanager.security.permission.SecurityPermission;
import pers.eastwind.billmanager.security.service.SecurityInitializer;
import pers.eastwind.billmanager.security.service.AuthorizeService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityInitializerTest extends BaseServiceTest {
    @Autowired
    private SecurityInitializer securityInitializer;
    @Autowired
    private AuthorizeService authorizeService;

    @Test
    void shouldCreateDefaultRolesAndGrantAdminAllPermissions() {
        securityInitializer.run(null);

        RoleDTO admin = authorizeService.getRoles().stream()
                .filter(role -> "ADMIN".equals(role.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(admin);
        RoleDTO user = authorizeService.getRoles().stream()
                .filter(role -> "USER".equals(role.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(user);

        List<String> codes = authorizeService.getRolePermissions(admin.getId()).stream()
                .map(PermissionDTO::getCode)
                .toList();
        long total = authorizeService.getPermissions().values().stream()
                .flatMap(List::stream)
                .count();
        assertEquals(total, codes.size());
        assertTrue(codes.contains(SecurityPermission.QUERY.getCode()));
        assertTrue(codes.contains(SecurityPermission.UPDATE.getCode()));
        assertTrue(codes.contains("serviceBill:assign"));
        assertTrue(codes.contains("serviceBill:data-all"));
    }
}
