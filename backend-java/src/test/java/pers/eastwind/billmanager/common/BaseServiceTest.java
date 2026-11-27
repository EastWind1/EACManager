package pers.eastwind.billmanager.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.company.service.CompanyService;
import pers.eastwind.billmanager.reimburse.service.ReimburseStatisticService;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.model.RoleDTO;
import pers.eastwind.billmanager.security.service.AuthenticateService;
import pers.eastwind.billmanager.security.service.AuthorizeService;
import pers.eastwind.billmanager.servicebill.service.ServiceBillQueryService;
import pers.eastwind.billmanager.servicebill.service.ServiceBillStatisticService;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.service.UserService;

import java.util.List;

@SpringBootTest
@Transactional
public class BaseServiceTest {
    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticateService authenticateService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    protected void setUp() {
        // 集成测试共享应用实例，权限/查询/统计缓存不能跨用例残留
        for (String name : List.of(AuthenticateService.AUTH_USER_CACHE, CompanyService.COMPANY_CACHE,
                ServiceBillQueryService.QUERY_CACHE, ServiceBillStatisticService.AMOUNT_STAT_CACHE,
                ReimburseStatisticService.AMOUNT_STAT_CACHE)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                // RedisCache.clear() 可能异步执行，测试里用 invalidate() 保证立即失效
                cache.invalidate();
            }
        }
        setAdmin();
    }

    /**
     * 使用真实数据库用户作为管理员：空库走初始化接口创建，已有数据的库复用现成管理员。
     * 用户与角色关联都在测试事务内，用例结束随事务回滚。
     */
    protected void setAdmin() {
        authenticate(ensureAdmin().getUsername());
    }

    /**
     * 设置普通用户，同样先落库再放进 SecurityContext
     */
    protected void setUser() {
        authenticate(ensureAdmin().getUsername());

        UserDTO dto = new UserDTO();
        dto.setUsername("baseTestUser");
        dto.setPassword("User123456");
        dto.setName("测试用户");
        dto.setRoles(List.of(new RoleDTO(null, "USER", "普通用户", 2, false)));
        authenticate(userService.create(dto).getUsername());
    }

    private UserDTO ensureAdmin() {
        if (userService.isInitializationRequired()) {
            UserDTO dto = new UserDTO();
            dto.setUsername("testAdmin");
            dto.setPassword("Admin12345");
            dto.setName("测试管理员");
            return userService.initialize(dto);
        }

        return userService.findEnabledUsersByRole(AuthorizeService.ADMIN_ROLE_CODE).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库中没有启用的管理员用户"));
    }

    private void authenticate(String username) {
        AuthorityUser user = authenticateService.loadUserByUsername(username);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities()));
    }
}
