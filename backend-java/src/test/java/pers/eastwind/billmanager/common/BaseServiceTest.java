package pers.eastwind.billmanager.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.user.model.AuthorityRole;
import pers.eastwind.billmanager.user.model.User;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class BaseServiceTest {
    @BeforeEach
    void setUp() {
       setAdmin();
    }

    /**
     * 设置管理员
     */
    protected void setAdmin() {
        User admin = new User();
        admin.setId(1);
        admin.setUsername("admin");
        admin.setAuthority(AuthorityRole.ROLE_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, "",admin.getAuthorities()));
    }

    /**
     * 设置普通用户
     */
    protected void setUser() {
        User user = new User();
        user.setId(1);
        user.setUsername("admin");
        user.setAuthority(AuthorityRole.ROLE_USER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,"", user.getAuthorities()));
    }
}
