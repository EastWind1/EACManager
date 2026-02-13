package test

import (
	"backend-go/internal/module/user"
	"backend-go/internal/module/user/model"
	"backend-go/internal/module/user/service"
	"backend-go/internal/pkg/auth"
	"backend-go/internal/pkg/result"
	"strconv"
	"testing"

	"github.com/stretchr/testify/suite"
)

// UserServiceTest 用户服务测试
type UserServiceTest struct {
	*BaseServiceTest
	srv      *service.UserService
	testUser *model.UserDTO
}

func NewUserServiceTest() *UserServiceTest {
	base := NewBaseServiceTest()
	return &UserServiceTest{
		BaseServiceTest: base,
		srv:             user.SetupForTest(base.appCtx),
	}
}

func (s *UserServiceTest) SetupTest() {
	s.BaseServiceTest.SetupTest()
	s.testUser = &model.UserDTO{
		Username:  "testUser",
		Password:  new("password123"),
		Name:      "测试用户",
		Authority: "ROLE_ADMIN",
	}
}

func TestUserService(t *testing.T) {
	suite.Run(t, NewUserServiceTest())
}

func (s *UserServiceTest) TestCreate() {
	created, err := s.srv.Create(s.ctx, s.testUser)
	s.NoError(err)

	s.NotZero(created.ID)
	s.Equal("testUser", created.Username)
	s.Equal("测试用户", created.Name)
}

func (s *UserServiceTest) TestFindByUsername() {
	_, err := s.srv.Create(s.ctx, s.testUser)
	s.NoError(err)

	found, err := s.srv.FindByUsername(s.ctx, s.testUser.Username)
	s.NoError(err)

	s.NotNil(found)
	s.Equal(s.testUser.Username, found.Username)
}

func (s *UserServiceTest) TestLogin() {
	_, err := s.srv.Create(s.ctx, s.testUser)
	s.NoError(err)

	res, err := s.srv.Login(s.ctx, "testUser", "password123", "test")
	s.NoError(err)

	s.NotNil(res)
	s.NotEmpty(res.Token)
	s.NotNil(res.User)
	s.Equal("testUser", res.User.Username)
}

func (s *UserServiceTest) TestGetAll() {
	for i := 0; i < 3; i++ {
		userDTO := &model.UserDTO{
			Username:  "user" + strconv.Itoa(i),
			Password:  new("password"),
			Name:      "用户" + strconv.Itoa(i),
			Authority: auth.RoleAdmin,
		}
		_, err := s.srv.Create(s.ctx, userDTO)
		s.NoError(err)
	}

	queryParam := &result.QueryParam{
		PageIndex: new(1),
		PageSize:  new(10),
	}
	res, err := s.srv.GetAll(s.ctx, queryParam)
	s.NoError(err)

	s.GreaterOrEqual(res.TotalCount, 2)
}

func (s *UserServiceTest) TestUpdate() {
	created, err := s.srv.Create(s.ctx, s.testUser)
	s.NoError(err)

	created.Name = "更新后的名称"
	created.Phone = "13900139000"
	updated, err := s.srv.Update(s.ctx, created)
	s.NoError(err)

	s.Equal("更新后的名称", updated.Name)
	s.Equal("13900139000", updated.Phone)
}

func (s *UserServiceTest) TestDisable() {
	_, err := s.srv.Create(s.ctx, s.testUser)
	s.NoError(err)
	err = s.srv.Disable(s.ctx, s.testUser.Username)
	s.NoError(err)
}
