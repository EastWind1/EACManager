package bill_test

import (
	"backend-go/internal/attach"
	"backend-go/internal/bill"
	"backend-go/internal/test"
	"backend-go/pkg/result"
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

// BillServiceTest 业务服务测试
type BillServiceTest struct {
	test.BaseSuite
	srv      *bill.Service
	testBill *bill.DTO
}

func TestBillService(t *testing.T) {
	suite.Run(t, new(BillServiceTest))
}

func (s *BillServiceTest) SetupSuite() {
	s.BaseSuite.SetupSuite()
	s.srv = s.App.Bill
}

func (s *BillServiceTest) SetupTest() {
	s.BaseSuite.SetupTest()
	s.testBill = &bill.DTO{
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.DetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  2,
				Subtotal:  2000.00,
			},
		},
	}
}

func (s *BillServiceTest) TestCreate() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	s.NotZero(created.ID)
	s.NotEmpty(created.Number)
	s.Equal(bill.Created, created.State)
}

func (s *BillServiceTest) TestInvalidNumberCreate() {
	s.testBill.Number = "BILL/TEST"
	_, err := s.srv.Create(s.Ctx, s.testBill)
	s.Error(err)
}

func (s *BillServiceTest) TestFindByID() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	found, err := s.srv.FindByID(s.Ctx, created.ID)
	s.NoError(err)

	s.NotNil(found)
	s.Equal(s.testBill.ProjectName, found.ProjectName)
}

func (s *BillServiceTest) TestFindByParam() {
	for i := range 3 {
		newBill := &bill.DTO{
			State:          bill.Created,
			ProjectName:    "测试项目" + strconv.Itoa(i),
			ProjectAddress: "测试地址" + strconv.Itoa(i),
			TotalAmount:    2000.00,
			OrderDate:      new(time.Time),
			Details: []bill.DetailDTO{
				{
					Device:    "测试设备" + strconv.Itoa(i),
					UnitPrice: 1000.00,
					Quantity:  2,
					Subtotal:  2000.00,
				},
			},
		}
		_, err := s.srv.Create(s.Ctx, newBill)
		s.NoError(err)
	}

	queryParam := &bill.QueryParam{
		ProjectName: "测试项目",
		PageIndex:   new(1),
		PageSize:    new(10),
	}
	bills, err := s.srv.FindByParam(s.Ctx, queryParam)
	s.NoError(err)

	s.GreaterOrEqual(bills.TotalCount, 3)
}

func (s *BillServiceTest) TestUpdate() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	created.ProjectName = "更新后的项目"
	updated, err := s.srv.Update(s.Ctx, created)
	s.NoError(err)

	s.Equal("更新后的项目", updated.ProjectName)
}

func (s *BillServiceTest) TestDelete() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	res, err := s.srv.Delete(s.Ctx, []uint{created.ID})
	s.NoError(err)

	s.NotNil(res)

	_, err = s.srv.FindByID(s.Ctx, created.ID)
	s.Error(err)
}

func (s *BillServiceTest) TestFinish() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)
	_, err = s.srv.Process(s.Ctx, []uint{created.ID})
	s.NoError(err)
	_, err = s.srv.Processed(s.Ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	_, err = s.srv.Finish(s.Ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)

	found, err := s.srv.FindByID(s.Ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Finished, found.State)
}

func (s *BillServiceTest) TestValidateAmountError() {
	invalidBill := &bill.DTO{
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.DetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  1,
				Subtotal:  1500.00,
			},
		},
	}

	_, err := s.srv.Create(s.Ctx, invalidBill)
	s.Error(err)
	s.Contains(err.Error(), "明细金额有误")

	invalidBill2 := &bill.DTO{
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    1500.00,
		OrderDate:      new(time.Time),
		Details: []bill.DetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  1,
				Subtotal:  1000.00,
			},
		},
	}

	_, err = s.srv.Create(s.Ctx, invalidBill2)
	s.Error(err)
	s.Contains(err.Error(), "总金额有误")
}

func (s *BillServiceTest) TestFindWithEmptyParam() {
	_, err := s.srv.FindByParam(s.Ctx, nil)
	s.Error(err)
	s.Contains(err.Error(), "查询参数为空")
}

func (s *BillServiceTest) TestCreateWithExistingID() {
	newBill := &bill.DTO{
		ID:             123, // 提供了ID，应该报错
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.DetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  2,
				Subtotal:  2000.00,
			},
		},
	}

	_, err := s.srv.Create(s.Ctx, newBill)
	s.Error(err)
	s.Contains(err.Error(), "单据 ID 自动生成")
}

func (s *BillServiceTest) TestUpdateWithEmptyID() {
	newBill := &bill.DTO{
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.DetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  2,
				Subtotal:  2000.00,
			},
		},
	}

	_, err := s.srv.Update(s.Ctx, newBill)
	s.Error(err)
	s.Contains(err.Error(), "单据 ID 为空")
}

func (s *BillServiceTest) TestUpdateNonExistentBill() {
	newBill := &bill.DTO{
		ID:             999999,
		State:          bill.Created,
		ProjectName:    "测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    2000.00,
		OrderDate:      new(time.Time),
		Details: []bill.DetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 1000.00,
				Quantity:  2,
				Subtotal:  2000.00,
			},
		},
	}

	_, err := s.srv.Update(s.Ctx, newBill)
	s.Error(err)
	s.Contains(err.Error(), "单据不存在")
}

func (s *BillServiceTest) TestFindNonExistentID() {
	_, err := s.srv.FindByID(s.Ctx, 999999)
	s.Error(err)
	s.Contains(err.Error(), "单据不存在")
}

func (s *BillServiceTest) TestDeleteNonCreatedState() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.Ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.srv.Delete(s.Ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非创建状态不能删除")
}

func (s *BillServiceTest) TestProcessWithEmptyIDs() {
	res, err := s.srv.Process(s.Ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestProcessNonCreatedState() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.Ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.srv.Process(s.Ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非创建状态的单据不能处理")
}

func (s *BillServiceTest) TestProcessedNonProcessingState() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	res, err := s.srv.Processed(s.Ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理中状态的单据不能处理完成")
}

func (s *BillServiceTest) TestFinishNonProcessedState() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)
	res, err := s.srv.Finish(s.Ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理完成状态的单据不能完成")
}

func (s *BillServiceTest) TestDeleteWithEmptyIDs() {
	res, err := s.srv.Delete(s.Ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestProcessedWithEmptyIDs() {
	res, err := s.srv.Processed(s.Ctx, []uint{}, nil)
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestFinishWithEmptyIDs() {
	res, err := s.srv.Finish(s.Ctx, []uint{}, nil)
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

// TestCancelProcess tests CancelProcess business logic
func (s *BillServiceTest) TestCancelProcessWithEmptyIDs() {
	res, err := s.srv.CancelProcess(s.Ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestCancelProcessNonExistentBill() {
	res, err := s.srv.CancelProcess(s.Ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BillServiceTest) TestCancelProcessNonProcessingState() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	res, err := s.srv.CancelProcess(s.Ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理中状态的单据不能取消处理")
}

func (s *BillServiceTest) TestCancelProcessSuccess() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.Ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.srv.CancelProcess(s.Ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.srv.FindByID(s.Ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Created, found.State)
	// ProcessedDate should be nil after cancel
	s.Nil(found.ProcessedDate)
}

// TestCancelProcessed tests CancelProcessed business logic
func (s *BillServiceTest) TestCancelProcessedWithEmptyIDs() {
	res, err := s.srv.CancelProcessed(s.Ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestCancelProcessedNonExistentBill() {
	res, err := s.srv.CancelProcessed(s.Ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BillServiceTest) TestCancelProcessedNonProcessedState() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	res, err := s.srv.CancelProcessed(s.Ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理完成状态的单据不能取消处理完成")
}

func (s *BillServiceTest) TestCancelProcessedSuccess() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.Ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.srv.Processed(s.Ctx, []uint{created.ID}, now)
	s.NoError(err)

	res, err := s.srv.CancelProcessed(s.Ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.srv.FindByID(s.Ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Processing, found.State)
	// ProcessedDate should be nil after cancel
	s.Nil(found.ProcessedDate)
}

// TestCancelFinish tests CancelFinish business logic
func (s *BillServiceTest) TestCancelFinishWithEmptyIDs() {
	res, err := s.srv.CancelFinish(s.Ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestCancelFinishNonExistentBill() {
	res, err := s.srv.CancelFinish(s.Ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BillServiceTest) TestCancelFinishNonFinishedState() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.Ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.srv.Processed(s.Ctx, []uint{created.ID}, now)
	s.NoError(err)

	res, err := s.srv.CancelFinish(s.Ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非完成状态的单据不能取消完成")
}

func (s *BillServiceTest) TestCancelFinishSuccess() {
	created, err := s.srv.Create(s.Ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.Ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.srv.Processed(s.Ctx, []uint{created.ID}, now)
	s.NoError(err)

	finishTime := new(time.Time)
	_, err = s.srv.Finish(s.Ctx, []uint{created.ID}, finishTime)
	s.NoError(err)

	res, err := s.srv.CancelFinish(s.Ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.srv.FindByID(s.Ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Processed, found.State)
	// FinishedDate should be nil after cancel
	s.Nil(found.FinishedDate)
}

// BillStatServiceTest 服务单统计服务集成测试
type BillStatServiceTest struct {
	test.BaseSuite
	srv     *bill.Service
	statSrv *bill.StatisticService
}

func TestBillStatService(t *testing.T) {
	suite.Run(t, new(BillStatServiceTest))
}

func (s *BillStatServiceTest) SetupSuite() {
	s.BaseSuite.SetupSuite()
	s.srv, s.statSrv = s.App.Bill, s.App.BillStat
}

func (s *BillStatServiceTest) SetupTest() {
	s.BaseSuite.SetupTest()
	// 月度金额统计带缓存，逐个用例前清空，避免脏数据串扰
	s.App.Cache.Clear()
}

func (s *BillStatServiceTest) TestCountBillsByState() {
	createdCount, processingCount := 2, 1

	before, err := s.statSrv.CountBillsByState(s.Ctx)
	s.NoError(err)

	for range createdCount {
		_, err := s.srv.Create(s.Ctx, s.newBill(1000))
		s.NoError(err)
	}
	for range processingCount {
		billDTO, err := s.srv.Create(s.Ctx, s.newBill(2000))
		s.NoError(err)
		_, err = s.srv.Process(s.Ctx, []uint{billDTO.ID})
		s.NoError(err)
	}

	after, err := s.statSrv.CountBillsByState(s.Ctx)
	s.NoError(err)
	s.Equal((*before)["CREATED"]+createdCount, (*after)["CREATED"])
	s.Equal((*before)["PROCESSING"]+processingCount, (*after)["PROCESSING"])
	s.Equal((*before)["PROCESSED"], (*after)["PROCESSED"])
}

// newBill 构建金额合法的新建单据
func (s *BillStatServiceTest) newBill(total float64) *bill.DTO {
	return &bill.DTO{
		State:          bill.Created,
		ProjectName:    "统计测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    total,
		OrderDate:      new(time.Now()),
		Details: []bill.DetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: total,
				Quantity:  1,
				Subtotal:  total,
			},
		},
	}
}

// createProcessedBill 创建并将单据推进到“处理完成”状态，processedDate 写入完成日期
func (s *BillStatServiceTest) createProcessedBill(total float64, processedDate time.Time) *bill.DTO {
	billDTO, err := s.srv.Create(s.Ctx, s.newBill(total))
	s.NoError(err)
	_, err = s.srv.Process(s.Ctx, []uint{billDTO.ID})
	s.NoError(err)
	date := processedDate
	_, err = s.srv.Processed(s.Ctx, []uint{billDTO.ID}, &date)
	s.NoError(err)
	return billDTO
}

func monthSumToMap(rows []result.MonthSumAmount) map[string]float64 {
	m := make(map[string]float64, len(rows))
	for _, row := range rows {
		m[row.Month] = row.Amount
	}
	return m
}

// BillAttachTest 服务单↔附件关联测试
type BillAttachTest struct {
	test.BaseSuite
	attachSrv *attach.Service
	srv       *bill.Service
}

func TestBillAttach(t *testing.T) {
	suite.Run(t, new(BillAttachTest))
}

func (s *BillAttachTest) SetupSuite() {
	s.BaseSuite.SetupSuite()
	s.attachSrv = s.App.Attach
	s.srv = s.App.Bill
}

func (s *BillAttachTest) upload(name string) attach.DTO {
	headers, err := s.UploadFileHeaders(name, "attach content")
	s.NoError(err)
	attaches, err := s.attachSrv.Upload(s.Ctx, bill.AttachNamespace, headers)
	s.NoError(err)
	s.Len(attaches, 1)
	return attaches[0]
}

func (s *BillAttachTest) newBillDTO(attachments ...attach.DTO) *bill.DTO {
	return &bill.DTO{
		ProjectName: "附件关联测试项目",
		TotalAmount: 100.00,
		OrderDate:   new(time.Now()),
		Attachments: attachments,
		Details: []bill.DetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 100.00,
				Quantity:  1,
				Subtotal:  100.00,
			},
		},
	}
}

// TestAttachRelation 新增时提交附件并建立关联
func (s *BillAttachTest) TestAttachRelation() {
	attachment := s.upload("服务单附件.txt")
	s.Positive(attachment.ID)

	created, err := s.srv.Create(s.Ctx, s.newBillDTO(attachment))
	s.NoError(err)
	s.Len(created.Attachments, 1)

	created.Attachments = nil
	updated, err := s.srv.Update(s.Ctx, created)
	s.NoError(err)
	s.Empty(updated.Attachments)

	var remaining int64
	s.NoError(s.Tx.Raw("select count(*) from attachment where id = ?", attachment.ID).Scan(&remaining).Error)
	s.Zero(remaining)
}

// TestCommittedAttachCannotBeReused 已提交的附件不能被另一张单据复用
func (s *BillAttachTest) TestCommittedAttachCannotBeReused() {
	created, err := s.srv.Create(s.Ctx, s.newBillDTO(s.upload("已提交附件.txt")))
	s.NoError(err)
	s.Len(created.Attachments, 1)

	_, err = s.srv.Create(s.Ctx, s.newBillDTO(created.Attachments[0]))
	s.Error(err)
	s.Contains(err.Error(), "附件不存在或已被提交")
}
