package test

import (
	"backend-go/internal/module/company"
	"backend-go/internal/module/company/model"
	"backend-go/internal/module/company/service"
	"backend-go/internal/pkg/result"
	"strconv"
	"testing"

	"github.com/stretchr/testify/suite"
)

// CompanyServiceTest CompanyService集成测试
type CompanyServiceTest struct {
	*BaseServiceTest
	srv     *service.CompanyService
	testCom *model.CompanyDTO
}

func NewCompanyServiceTest() *CompanyServiceTest {
	base := NewBaseServiceTest()
	return &CompanyServiceTest{
		BaseServiceTest: base,
		srv:             company.SetupForTest(base.appCtx),
	}
}

func TestCompanyService(t *testing.T) {
	suite.Run(t, NewCompanyServiceTest())
}

func (s *CompanyServiceTest) SetupTest() {
	s.BaseServiceTest.SetupTest()
	s.testCom = &model.CompanyDTO{
		Name:         "测试公司",
		ContactName:  "联系人",
		ContactPhone: "13800138000",
	}
}
func (s *CompanyServiceTest) TestCreate() {
	created, err := s.srv.Create(s.ctx, s.testCom)
	s.NoError(err)

	s.NotZero(created.ID)
	s.Equal(s.testCom.Name, created.Name)
	s.Equal(s.testCom.ContactName, created.ContactName)
}
func (s *CompanyServiceTest) TestFindEnabled() {
	for i := range 2 {
		dto := &model.CompanyDTO{
			Name:         "公司" + strconv.Itoa(i),
			ContactName:  "联系人" + strconv.Itoa(i),
			ContactPhone: "1380013800" + strconv.Itoa(i),
		}
		_, err := s.srv.Create(s.ctx, dto)
		s.NoError(err)
	}

	res, err := s.srv.FindEnabled(s.ctx, new(result.QueryParam{}))
	s.NoError(err)
	s.GreaterOrEqual(res.TotalCount, 3)
}

func (s *CompanyServiceTest) TestFindByName() {
	_, err := s.srv.Create(s.ctx, s.testCom)
	s.NoError(err)

	found, err := s.srv.FindByName(s.ctx, s.testCom.Name)
	s.NoError(err)

	s.NotEmpty(found)
	s.Equal(s.testCom.Name, found[0].Name)
}

func (s *CompanyServiceTest) TestUpdate() {
	created, err := s.srv.Create(s.ctx, s.testCom)
	s.NoError(err)

	created.ContactName = "更新后的联系人"
	created.ContactPhone = "13900139000"
	created.Address = "更新后的地址"
	updated, err := s.srv.Update(s.ctx, created)
	s.NoError(err)

	s.Equal("更新后的联系人", updated.ContactName)
	s.Equal("13900139000", updated.ContactPhone)
	s.Equal("更新后的地址", updated.Address)
}

func (s *CompanyServiceTest) TestDisable() {
	created, err := s.srv.Create(s.ctx, s.testCom)
	s.NoError(err)

	err = s.srv.Disable(s.ctx, created.ID)
	s.NoError(err)
	res, err := s.srv.FindByName(s.ctx, created.Name)
	s.NoError(err)
	s.Empty(res)
}
