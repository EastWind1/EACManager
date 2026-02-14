package test

import (
	"backend-go/internal/module/attach"
	"backend-go/internal/module/reimburse"
	"backend-go/internal/module/reimburse/model"
	"backend-go/internal/module/reimburse/service"
	"backend-go/internal/pkg/result"
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

// ReimburseServiceTest 报销服务测试
type ReimburseServiceTest struct {
	*BaseServiceTest
	srv      *service.ReimburseService
	testReim *model.ReimbursementDTO
}

func NewReimburseServiceTest() *ReimburseServiceTest {
	base := NewBaseServiceTest()
	attachSrv, _ := attach.SetupForTest(base.appCtx)
	return &ReimburseServiceTest{
		BaseServiceTest: base,
		srv:             reimburse.SetupForTest(base.appCtx, attachSrv),
	}
}

func TestReimburseService(t *testing.T) {
	suite.Run(t, NewReimburseServiceTest())
}

func (s *ReimburseServiceTest) SetupTest() {
	s.BaseServiceTest.SetupTest()
	s.testReim = &model.ReimbursementDTO{
		Summary:       "测试报销",
		TotalAmount:   1000.00,
		ReimburseDate: new(time.Now()),
		Remark:        "测试备注",
		Details: []model.ReimburseDetailDTO{
			{
				Name:   "明细项目",
				Amount: 1000.00,
			},
		},
	}
}

func (s *ReimburseServiceTest) TestCreate() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	s.NotZero(created.ID)
	s.NotEmpty(created.Number)
	s.Equal(model.ReimburseStateCreated, created.State)
}

func (s *ReimburseServiceTest) TestFindByID() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)

	s.NotNil(found)
	s.Equal(s.testReim.Summary, found.Summary)
}

func (s *ReimburseServiceTest) TestFindByParam() {
	for i := range 3 {
		reim := &model.ReimbursementDTO{
			Summary:       "测试报销" + strconv.Itoa(i),
			TotalAmount:   1000.00,
			ReimburseDate: new(time.Time),
			Remark:        "测试备注" + strconv.Itoa(i),
			Details: []model.ReimburseDetailDTO{
				{
					Name:   "明细项目" + strconv.Itoa(i),
					Amount: 1000.00,
				},
			},
		}
		_, err := s.srv.Create(s.ctx, reim)
		s.NoError(err)
	}

	queryParam := &model.ReimburseQueryParam{
		Summary: "测试报销",
		QueryParam: result.QueryParam{
			PageIndex: new(1),
			PageSize:  new(10),
		},
	}
	res, err := s.srv.FindByParam(s.ctx, queryParam)
	s.NoError(err)

	s.GreaterOrEqual(res.TotalCount, 3)
}

func (s *ReimburseServiceTest) TestUpdate() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	created.Summary = "更新后的摘要"
	created.TotalAmount = 2000.00
	updated, err := s.srv.Update(s.ctx, created)
	s.NoError(err)

	s.Equal("更新后的摘要", updated.Summary)
	s.Equal(2000.00, updated.TotalAmount)
}

func (s *ReimburseServiceTest) TestDelete() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	_, err = s.srv.Delete(s.ctx, []uint{created.ID})
	s.NoError(err)

	_, err = s.srv.FindByID(s.ctx, created.ID)
	s.Error(err)
}

func (s *ReimburseServiceTest) TestProcess() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(model.ReimburseStateProcessing, found.State)
}

func (s *ReimburseServiceTest) TestFinish() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)
	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	_, err = s.srv.Finish(s.ctx, []uint{created.ID})
	s.NoError(err)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(model.ReimburseStateFinished, found.State)
}

func (s *ReimburseServiceTest) TestExport() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	path, err := s.srv.Export(s.ctx, []uint{created.ID})
	s.NoError(err)

	s.NotEmpty(path)
}

func (s *ReimburseServiceTest) TestGenerateNumber() {
	number := s.srv.GenerateNumber()

	s.NotEmpty(number)
}
