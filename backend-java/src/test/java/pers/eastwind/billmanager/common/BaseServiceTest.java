package pers.eastwind.billmanager.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.security.model.RoleDTO;
import pers.eastwind.billmanager.user.model.UserDTO;

import java.util.List;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class BaseServiceTest {
    @BeforeEach
    protected void setUp() {
        setAdmin();
    }

    /**
     * 设置管理员；用户 id 1 在初始化数据中拥有 ADMIN 角色
     */
    protected void setAdmin() {
        UserDTO admin = new UserDTO();
        admin.setId(1);
        admin.setUsername("admin");
        admin.setRoles(List.of(new RoleDTO(1, "ADMIN", "管理员", 1, false)));
        admin.setPermissions(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, "", admin.getAuthorities()));
    }

    /**
     * 设置普通用户
     */
    protected void setUser() {
        UserDTO user = new UserDTO();
        user.setId(1);
        user.setUsername("admin");
        user.setRoles(List.of(new RoleDTO(2, "USER", "普通用户", 2, false)));
        user.setPermissions(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities()));
    }
}
