package pers.eastwind.billmanager.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.security.repository.UserRoleRepository;
import pers.eastwind.billmanager.user.model.UserDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInitializationTest extends BaseServiceTest {
    @Autowired
    private UserService userService;
    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void shouldCreateFirstAdminAndAllowLogin() throws Exception {
        userRoleRepository.deleteAll();
        userRoleRepository.flush();
        assertTrue(userService.isInitializationRequired());
        var user1 = new UserDTO();
        user1.setUsername("initAdmin");
        user1.setPassword("Admin12345");
        user1.setName("初始化管理员");
        UserDTO admin = userService.initialize(user1);

        assertEquals("initAdmin", admin.getUsername());
        assertFalse(userService.isInitializationRequired());

        String hashedPassword = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest("Admin12345:initAdmin".getBytes(StandardCharsets.UTF_8)));
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
        userRoleRepository.deleteAll();
        userRoleRepository.flush();
        var user = new UserDTO();
        user.setUsername("initAdmin");
        user.setPassword("short");
        user.setName("初始化管理员");
        assertThrows(RuntimeException.class, () -> userService.initialize(user));
    }
}
