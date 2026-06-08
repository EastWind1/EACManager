package test

import (
	"backend-go/internal/attach"
	"backend-go/internal/bill"
	"backend-go/pkg/result"
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

// BizServiceTest 业务服务测试
type BizServiceTest struct {
	*BaseServiceTest
	bizSrv   *bill.BizService
	statsSrv *bill.StatisticService
	testBill *bill.ServiceBillDTO
}

func NewBizServiceTest() *BizServiceTest {
	base := NewBaseServiceTest()
	attachSrv, _ := attach.SetupForTest(base.appCtx)
	bizSrv, statsSrv := bill.SetupForTest(base.appCtx, attachSrv)
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
	s.testBill = &bill.ServiceBillDTO{
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.ServiceBillDetailDTO{
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
	s.Equal(bill.Created, created.State)
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
		newBill := &bill.ServiceBillDTO{
			State:          bill.Created,
			ProjectName:    "测试项目" + strconv.Itoa(i),
			ProjectAddress: "测试地址" + strconv.Itoa(i),
			TotalAmount:    2000.00,
			OrderDate:      new(time.Time),
			Details: []bill.ServiceBillDetailDTO{
				{
					Device:    "测试设备" + strconv.Itoa(i),
					UnitPrice: 1000.00,
					Quantity:  2,
					Subtotal:  2000.00,
				},
			},
		}
		_, err := s.bizSrv.Create(s.ctx, newBill)
		s.NoError(err)
	}

	queryParam := &bill.ServiceBillQueryParam{
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
	s.Equal(bill.Finished, found.State)
}

func (s *BizServiceTest) TestValidateAmountError() {
	invalidBill := &bill.ServiceBillDTO{
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.ServiceBillDetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  1,
				Subtotal:  1500.00,
			},
		},
	}

	_, err := s.bizSrv.Create(s.ctx, invalidBill)
	s.Error(err)
	s.Contains(err.Error(), "明细金额有误")

	invalidBill2 := &bill.ServiceBillDTO{
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    1500.00,
		OrderDate:      new(time.Time),
		Details: []bill.ServiceBillDetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  1,
				Subtotal:  1000.00,
			},
		},
	}

	_, err = s.bizSrv.Create(s.ctx, invalidBill2)
	s.Error(err)
	s.Contains(err.Error(), "总金额有误")
}

func (s *BizServiceTest) TestFindWithEmptyParam() {
	_, err := s.bizSrv.FindByParam(s.ctx, nil)
	s.Error(err)
	s.Contains(err.Error(), "查询参数为空")
}

func (s *BizServiceTest) TestCreateWithExistingID() {
	newBill := &bill.ServiceBillDTO{
		ID:             123, // 提供了ID，应该报错
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.ServiceBillDetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  2,
				Subtotal:  2000.00,
			},
		},
	}

	_, err := s.bizSrv.Create(s.ctx, newBill)
	s.Error(err)
	s.Contains(err.Error(), "单据 ID 自动生成")
}

func (s *BizServiceTest) TestUpdateWithEmptyID() {
	newBill := &bill.ServiceBillDTO{
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.ServiceBillDetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  2,
				Subtotal:  2000.00,
			},
		},
	}

	_, err := s.bizSrv.Update(s.ctx, newBill)
	s.Error(err)
	s.Contains(err.Error(), "单据 ID 为空")
}

func (s *BizServiceTest) TestUpdateNonExistentBill() {
	newBill := &bill.ServiceBillDTO{
		ID:             999999,
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.ServiceBillDetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  2,
				Subtotal:  2000.00,
			},
		},
	}

	_, err := s.bizSrv.Update(s.ctx, newBill)
	s.Error(err)
	s.Contains(err.Error(), "单据不存在")
}

func (s *BizServiceTest) TestFindNonExistentID() {
	_, err := s.bizSrv.FindByID(s.ctx, 999999)
	s.Error(err)
	s.Contains(err.Error(), "单据不存在")
}

func (s *BizServiceTest) TestDeleteNonCreatedState() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.bizSrv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.bizSrv.Delete(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非创建状态不能删除")
}

func (s *BizServiceTest) TestProcessWithEmptyIDs() {
	res, err := s.bizSrv.Process(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BizServiceTest) TestProcessNonCreatedState() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.bizSrv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.bizSrv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非创建状态的单据不能处理")
}

func (s *BizServiceTest) TestProcessedNonProcessingState() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	res, err := s.bizSrv.Processed(s.ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理中状态的单据不能处理完成")
}

func (s *BizServiceTest) TestFinishNonProcessedState() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)
	res, err := s.bizSrv.Finish(s.ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理完成状态的单据不能完成")
}

func (s *BizServiceTest) TestDeleteWithEmptyIDs() {
	res, err := s.bizSrv.Delete(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BizServiceTest) TestProcessedWithEmptyIDs() {
	res, err := s.bizSrv.Processed(s.ctx, []uint{}, nil)
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BizServiceTest) TestFinishWithEmptyIDs() {
	res, err := s.bizSrv.Finish(s.ctx, []uint{}, nil)
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

// TestCancelProcess tests CancelProcess business logic
func (s *BizServiceTest) TestCancelProcessWithEmptyIDs() {
	res, err := s.bizSrv.CancelProcess(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BizServiceTest) TestCancelProcessNonExistentBill() {
	res, err := s.bizSrv.CancelProcess(s.ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BizServiceTest) TestCancelProcessNonProcessingState() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	res, err := s.bizSrv.CancelProcess(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理中状态的单据不能取消处理")
}

func (s *BizServiceTest) TestCancelProcessSuccess() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.bizSrv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.bizSrv.CancelProcess(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.bizSrv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Created, found.State)
	// ProcessedDate should be nil after cancel
	s.Nil(found.ProcessedDate)
}

// TestCancelProcessed tests CancelProcessed business logic
func (s *BizServiceTest) TestCancelProcessedWithEmptyIDs() {
	res, err := s.bizSrv.CancelProcessed(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BizServiceTest) TestCancelProcessedNonExistentBill() {
	res, err := s.bizSrv.CancelProcessed(s.ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BizServiceTest) TestCancelProcessedNonProcessedState() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	res, err := s.bizSrv.CancelProcessed(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理完成状态的单据不能取消处理完成")
}

func (s *BizServiceTest) TestCancelProcessedSuccess() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.bizSrv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.bizSrv.Processed(s.ctx, []uint{created.ID}, now)
	s.NoError(err)

	res, err := s.bizSrv.CancelProcessed(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.bizSrv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Processing, found.State)
	// ProcessedDate should be nil after cancel
	s.Nil(found.ProcessedDate)
}

// TestCancelFinish tests CancelFinish business logic
func (s *BizServiceTest) TestCancelFinishWithEmptyIDs() {
	res, err := s.bizSrv.CancelFinish(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BizServiceTest) TestCancelFinishNonExistentBill() {
	res, err := s.bizSrv.CancelFinish(s.ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BizServiceTest) TestCancelFinishNonFinishedState() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.bizSrv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.bizSrv.Processed(s.ctx, []uint{created.ID}, now)
	s.NoError(err)

	res, err := s.bizSrv.CancelFinish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非完成状态的单据不能取消完成")
}

func (s *BizServiceTest) TestCancelFinishSuccess() {
	created, err := s.bizSrv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.bizSrv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.bizSrv.Processed(s.ctx, []uint{created.ID}, now)
	s.NoError(err)

	finishTime := new(time.Time)
	_, err = s.bizSrv.Finish(s.ctx, []uint{created.ID}, finishTime)
	s.NoError(err)

	res, err := s.bizSrv.CancelFinish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.bizSrv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Processed, found.State)
	// FinishedDate should be nil after cancel
	s.Nil(found.FinishedDate)
}
