package pers.eastwind.billmanager.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pers.eastwind.billmanager.common.BaseServiceTest;
import pers.eastwind.billmanager.common.model.PageResult;
import pers.eastwind.billmanager.common.model.QueryParam;
import pers.eastwind.billmanager.user.model.AuthorityRole;
import pers.eastwind.billmanager.user.model.UserDTO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserService 集成测试
 */
class UserServiceTest extends BaseServiceTest {

    @Autowired
    private UserService userService;

    private UserDTO testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserDTO();
        testUser.setUsername("innerTestUser");
        testUser.setPassword("password123");
        testUser.setName("测试用户");
        testUser.setAuthority(AuthorityRole.ROLE_USER);
    }


    @Test
    @DisplayName("测试获取所有用户列表")
    void shouldGetAllUsers() {
        QueryParam queryParam = new QueryParam();
        queryParam.setPageIndex(0);
        queryParam.setPageSize(10);

        PageResult<UserDTO> result = userService.getAll(queryParam);
        assertNotNull(result);
    }

    @Test
    @DisplayName("测试创建用户")
    void shouldCreateUser() {
        UserDTO createdUser = userService.create(testUser);

        assertNotNull(createdUser);
        assertNotNull(createdUser.getId());
        assertEquals(testUser.getUsername(), createdUser.getUsername());
        assertEquals(testUser.getName(), createdUser.getName());
        assertEquals(AuthorityRole.ROLE_USER, createdUser.getAuthority());
    }

    @Test
    @DisplayName("测试更新用户")
    void shouldUpdateUser() {
        UserDTO createdUser = userService.create(testUser);

        createdUser.setName("更新后的名称");
        createdUser.setPhone("13800138000");
        UserDTO updatedUser = userService.update(createdUser);

        assertNotNull(updatedUser);
        assertEquals("更新后的名称", updatedUser.getName());
        assertEquals("13800138000", updatedUser.getPhone());
    }

    @Test
    @DisplayName("测试禁用用户")
    void shouldDisableUser() {
        userService.create(testUser);
        userService.disable(testUser.getUsername());
        var createdUser = userService.loadUserByUsername(testUser.getUsername());
        assertTrue(createdUser.isDisabled());
    }

    @Test
    @DisplayName("测试登录成功")
    void shouldLoginSuccessfully() {
        userService.create(testUser);

        var result = userService.login(testUser.getUsername(), "password123");

        assertNotNull(result);
        assertNotNull(result.token());
        assertNotNull(result.user());
        assertEquals(testUser.getUsername(), result.user().getUsername());
    }

    @Test
    @DisplayName("测试登录失败 - 密码错误")
    void shouldFailLoginWithWrongPassword() {
        userService.create(testUser);
        assertThrows(RuntimeException.class, () -> {
          userService.login(testUser.getUsername(), "wrongpassword");
        });
    }

    @Test
    @DisplayName("测试加载用户信息")
    void shouldLoadUserByUsername() {
        userService.create(testUser);
        var user = userService.loadUserByUsername(testUser.getUsername());
        assertNotNull(user);
        assertEquals(testUser.getUsername(), user.getUsername());
    }
}
