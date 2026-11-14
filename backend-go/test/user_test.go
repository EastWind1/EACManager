package test

import (
	"backend-go/internal/security"
	"backend-go/internal/user"
	"backend-go/pkg/result"
	"context"
	"strconv"
	"testing"

	"github.com/stretchr/testify/suite"
)

// UserServiceTest 用户服务测试
type UserServiceTest struct {
	BaseSuite
	srv      *user.Service
	testUser *user.DTO
}

func TestUserService(t *testing.T) {
	suite.Run(t, new(UserServiceTest))
}

func (s *UserServiceTest) SetupSuite() {
	s.BaseSuite.SetupSuite()
	s.srv = s.App.User
}

func (s *UserServiceTest) SetupTest() {
	s.BaseSuite.SetupTest()
	s.Ctx = s.adminContext(s.Ctx)
	s.testUser = &user.DTO{
		AuthorityUser: security.AuthorityUser{Username: "testUser", Password: "password123", Roles: []security.RoleDTO{{Code: "ADMIN"}}},
		Name:          "测试用户",
	}
}

// adminContext 准备一个管理员调用者：空库通过初始化接口创建首个管理员，
// 已有数据的库复用现成管理员
// 写入都在测试事务内，用例结束随事务回滚。
func (s *UserServiceTest) adminContext(ctx context.Context) context.Context {
	required, err := s.srv.IsInitializationRequired(ctx)
	s.NoError(err)
	if required {
		admin, err := s.srv.Initialize(ctx, &user.DTO{
			AuthorityUser: security.AuthorityUser{Username: "testAdmin", Password: "Password123"},
			Name:          "测试管理员",
		})
		s.NoError(err)
		return security.SetUserToCtx(ctx, &security.AuthorityUser{ID: admin.ID, Username: admin.Username})
	}

	admin, err := s.srv.FindEnabledUsersByRole(ctx, security.AdminRoleCode)
	s.NoError(err)
	s.NotEmpty(admin)
	return security.SetUserToCtx(ctx, &security.AuthorityUser{ID: admin[0].ID, Username: admin[0].Username})
}

func (s *UserServiceTest) TestCreate() {
	created, err := s.srv.Create(s.Ctx, s.testUser)
	s.NoError(err)

	s.NotZero(created.ID)
	s.Equal("testUser", created.Username)
	s.Equal("测试用户", created.Name)
}

func (s *UserServiceTest) TestFindByUsername() {
	_, err := s.srv.Create(s.Ctx, s.testUser)
	s.NoError(err)

	found, err := s.App.Security.Authenticate.FindByUsername(s.Ctx, s.testUser.Username)
	s.NoError(err)

	s.NotNil(found)
	s.Equal(s.testUser.Username, found.Username)
}

func (s *UserServiceTest) TestLogin() {
	_, err := s.srv.Create(s.Ctx, s.testUser)
	s.NoError(err)

	res, err := s.srv.Login(s.Ctx, "testUser", "password123")
	s.NoError(err)

	s.NotNil(res)
	s.NotEmpty(res.Token)
	s.NotNil(res.User)
	s.Equal("testUser", res.User.Username)
}

func (s *UserServiceTest) TestGetAll() {
	for i := range 3 {
		newUser := &user.DTO{
			AuthorityUser: security.AuthorityUser{Username: "user" + strconv.Itoa(i), Password: "password123", Roles: []security.RoleDTO{{Code: "ADMIN"}}},
			Name:          "用户" + strconv.Itoa(i),
		}
		_, err := s.srv.Create(s.Ctx, newUser)
		s.NoError(err)
	}

	queryParam := &result.QueryParam{
		PageIndex: new(1),
		PageSize:  new(10),
	}
	res, err := s.srv.GetAll(s.Ctx, queryParam)
	s.NoError(err)

	s.GreaterOrEqual(res.TotalCount, 2)
}

func (s *UserServiceTest) TestUpdate() {
	created, err := s.srv.Create(s.Ctx, s.testUser)
	s.NoError(err)

	created.Name = "更新后的名称"
	created.Phone = "13900139000"
	updated, err := s.srv.Update(s.Ctx, created)
	s.NoError(err)

	s.Equal("更新后的名称", updated.Name)
	s.Equal("13900139000", updated.Phone)
}

func (s *UserServiceTest) TestDisable() {
	_, err := s.srv.Create(s.Ctx, s.testUser)
	s.NoError(err)
	err = s.App.Security.Authenticate.Disable(s.Ctx, s.testUser.Username)
	s.NoError(err)
}

func (s *UserServiceTest) TestCreateDuplicateUsername() {
	_, err := s.srv.Create(s.Ctx, s.testUser)
	s.NoError(err)

	duplicate := &user.DTO{
		AuthorityUser: security.AuthorityUser{Username: "testUser", Password: "password123", Roles: []security.RoleDTO{{Code: "USER"}}},
		Name:          "重复用户",
	}
	_, err = s.srv.Create(s.Ctx, duplicate)
	s.Error(err)
	s.Contains(err.Error(), "用户名已存在")
}

func (s *UserServiceTest) TestLoginWithWrongPassword() {
	_, err := s.srv.Create(s.Ctx, s.testUser)
	s.NoError(err)

	_, err = s.srv.Login(s.Ctx, "testUser", "wrong-password")
	s.Error(err)
	s.Contains(err.Error(), "用户名或密码错误")
}

func (s *UserServiceTest) TestLoginDisabledUser() {
	_, err := s.srv.Create(s.Ctx, s.testUser)
	s.NoError(err)
	err = s.App.Security.Authenticate.Disable(s.Ctx, s.testUser.Username)
	s.NoError(err)

	_, err = s.srv.Login(s.Ctx, "testUser", "password123")
	s.Error(err)
	s.Contains(err.Error(), "用户已禁用")
}
