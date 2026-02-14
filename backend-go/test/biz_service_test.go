package test

import (
	"backend-go/internal/module/attach"
	"backend-go/internal/module/servicebill"
	"backend-go/internal/module/servicebill/model"
	"backend-go/internal/module/servicebill/service"
	"backend-go/internal/pkg/result"
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

// BizServiceTest 业务服务测试
type BizServiceTest struct {
	*BaseServiceTest
	bizSrv   *service.BizService
	statsSrv *service.StatisticService
	testBill *model.ServiceBillDTO
}

func NewBizServiceTest() *BizServiceTest {
	base := NewBaseServiceTest()
	attachSrv, _ := attach.SetupForTest(base.appCtx)
	bizSrv, statsSrv := servicebill.SetupForTest(base.appCtx, attachSrv)
	return &BizServiceTest{
		BaseServiceTest: base,
		bizSrv:          bizSrv,
		statsSrv:        statsSrv,
	}
}

func TestBizService(t *testing.T) {
	suite.Run(t, NewBizServiceTest())
}

func (s *BizServiceTest) SetupTest() {
	s.BaseServiceTest.SetupTest()
	s.testBill = &model.ServiceBillDTO{
		State:          model.ServiceBillStateCreated,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []model.ServiceBillDetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  2,
				Subtotal:  2000.00,
			},
		},
	}
}

func (s *BizServiceTest) TestCreate() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	s.NotZero(created.ID)
	s.NotEmpty(created.Number)
	s.Equal(model.ServiceBillStateCreated, created.State)
}

func (s *BizServiceTest) TestFindByID() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	found, err := s.bizSrv.FindByID(s.ctx, created.ID)
	s.NoError(err)

	s.NotNil(found)
	s.Equal(s.testBill.ProjectName, found.ProjectName)
}

func (s *BizServiceTest) TestFindByParam() {
	for i := range 3 {
		bill := &model.ServiceBillDTO{
			State:          model.ServiceBillStateCreated,
			ProjectName:    "测试项目" + strconv.Itoa(i),
			ProjectAddress: "测试地址" + strconv.Itoa(i),
			TotalAmount:    2000.00,
			OrderDate:      new(time.Time),
			Details: []model.ServiceBillDetailDTO{
				{
					Device:    "测试设备" + strconv.Itoa(i),
					UnitPrice: 1000.00,
					Quantity:  2,
					Subtotal:  2000.00,
				},
			},
		}
		_, err := s.bizSrv.Create(s.ctx, bill)
		s.NoError(err)
	}

	queryParam := &model.ServiceBillQueryParam{
		ProjectName: "测试项目",
		QueryParam: result.QueryParam{
			PageIndex: new(1),
			PageSize:  new(10),
		},
	}
	bills, err := s.bizSrv.FindByParam(s.ctx, queryParam)
	s.NoError(err)

	s.GreaterOrEqual(bills.TotalCount, 3)
}

func (s *BizServiceTest) TestUpdate() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	created.ProjectName = "更新后的项目"
	updated, err := s.bizSrv.Update(s.ctx, created)
	s.NoError(err)

	s.Equal("更新后的项目", updated.ProjectName)
}

func (s *BizServiceTest) TestDelete() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	res, err := s.bizSrv.Delete(s.ctx, []uint{created.ID})
	s.NoError(err)

	s.NotNil(res)

	_, err = s.bizSrv.FindByID(s.ctx, created.ID)
	s.Error(err)
}

func (s *BizServiceTest) TestFinish() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)
	_, err = s.bizSrv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	_, err = s.bizSrv.Processed(s.ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	_, err = s.bizSrv.Finish(s.ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)

	found, err := s.bizSrv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(model.ServiceBillStateFinished, found.State)
}
