package pers.eastwind.billmanager.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.security.service.SecurityService;
import pers.eastwind.billmanager.user.model.UserDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInitializationTest extends BaseServiceTest {
    @Autowired
    private UserService userService;
    @Autowired
    private SecurityService securityService;

    /**
     * 空库或已有数据的库都先清掉管理员角色，模拟尚未初始化的状态。
     */
    private void clearAdminRoles() {
        for (UserDTO admin : userService.findEnabledUsersByRole(SecurityService.ADMIN_ROLE_CODE)) {
            securityService.setUserRoles(admin.getId(), Set.of());
        }
    }

    @Test
    void shouldCreateFirstAdminAndAllowLogin() throws Exception {
        clearAdminRoles();
        assertTrue(userService.isInitializationRequired());
        String hashedPassword = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest("Admin12345:initAdmin".getBytes(StandardCharsets.UTF_8)));
        var user1 = new UserDTO();
        user1.setUsername("initAdmin");
        user1.setPassword(hashedPassword);
        user1.setName("初始化管理员");
        UserDTO admin = userService.initialize(user1);

        assertEquals("initAdmin", admin.getUsername());
        assertTrue(admin.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode())));
        assertFalse(admin.getPermissions().isEmpty());
        assertFalse(userService.isInitializationRequired());

        var login = userService.login("initAdmin", hashedPassword);
        assertEquals("initAdmin", login.user().getUsername());
        var user2 = new UserDTO();
        user2.setUsername("secondAdmin");
        user2.setPassword("Admin12345");
        user2.setName("初始化管理员");
        assertThrows(RuntimeException.class, () -> userService.initialize(user2));
    }

    @Test
    void shouldRejectInvalidInitializationPassword() {
        clearAdminRoles();
        var user = new UserDTO();
        user.setUsername("initAdmin");
        user.setPassword("short");
        user.setName("初始化管理员");
        assertThrows(RuntimeException.class, () -> userService.initialize(user));
    }
}
