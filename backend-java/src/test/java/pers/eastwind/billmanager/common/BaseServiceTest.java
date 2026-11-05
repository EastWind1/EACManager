package pers.eastwind.billmanager.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.security.model.RoleDTO;
import pers.eastwind.billmanager.security.service.SecurityService;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.service.UserService;

import java.util.List;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class BaseServiceTest {
    @Autowired
    private UserService userService;

    @BeforeEach
    protected void setUp() {
        setAdmin();
    }

    /**
     * 使用真实数据库用户作为管理员：空库走初始化接口创建，已有数据的库复用现成管理员。
     * 用户与角色关联都在测试事务内，用例结束随事务回滚。
     */
    protected void setAdmin() {
        authenticate(ensureAdmin());
    }

    /**
     * 设置普通用户，同样先落库再放进 SecurityContext
     */
    protected void setUser() {
        authenticate(ensureAdmin());

        UserDTO dto = new UserDTO();
        dto.setUsername("baseTestUser");
        dto.setPassword("User123456");
        dto.setName("测试用户");
        dto.setRoles(List.of(new RoleDTO(null, "USER", "普通用户", 2, false)));
        authenticate(userService.create(dto));
    }

    private UserDTO ensureAdmin() {
        if (userService.isInitializationRequired()) {
            UserDTO dto = new UserDTO();
            dto.setUsername("testAdmin");
            dto.setPassword("Admin12345");
            dto.setName("测试管理员");
            return userService.initialize(dto);
        }

        return userService.findEnabledUsersByRole(SecurityService.ADMIN_ROLE_CODE).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库中没有启用的管理员用户"));
    }

    private void authenticate(UserDTO user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities()));
    }
}
