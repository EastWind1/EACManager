package bill_test

import (
	"backend-go/internal/attach"
	"backend-go/internal/bill"
	"backend-go/internal/test_util"
	"backend-go/pkg/result"
	"context"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

// BillServiceTest 业务服务测试
type BillServiceTest struct {
	suite.Suite
	ctx      context.Context
	srv      *bill.Service
	testBill *bill.DTO
}

func TestBillService(t *testing.T) {
	suite.Run(t, new(BillServiceTest))
}

func (s *BillServiceTest) SetupSuite() {
	attachSvc, _ := attach.SetupForTest(test_util.AppCtx())
	s.srv, _ = bill.SetupForTest(test_util.AppCtx(), attachSvc)
}

func (s *BillServiceTest) SetupTest() {
	s.ctx = test_util.BeginTx(s.T())
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
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	s.NotZero(created.ID)
	s.NotEmpty(created.Number)
	s.Equal(bill.Created, created.State)
}

func (s *BillServiceTest) TestInvalidNumberCreate() {
	s.testBill.Number = "BILL/TEST"
	_, err := s.srv.Create(s.ctx, s.testBill)
	s.Error(err)
}

func (s *BillServiceTest) TestFindByID() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	found, err := s.srv.FindByID(s.ctx, created.ID)
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
		_, err := s.srv.Create(s.ctx, newBill)
		s.NoError(err)
	}

	queryParam := &bill.QueryParam{
		ProjectName: "测试项目",
		PageIndex:   new(1),
		PageSize:    new(10),
	}
	bills, err := s.srv.FindByParam(s.ctx, queryParam)
	s.NoError(err)

	s.GreaterOrEqual(bills.TotalCount, 3)
}

func (s *BillServiceTest) TestUpdate() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	created.ProjectName = "更新后的项目"
	updated, err := s.srv.Update(s.ctx, created)
	s.NoError(err)

	s.Equal("更新后的项目", updated.ProjectName)
}

func (s *BillServiceTest) TestDelete() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	res, err := s.srv.Delete(s.ctx, []uint{created.ID})
	s.NoError(err)

	s.NotNil(res)

	_, err = s.srv.FindByID(s.ctx, created.ID)
	s.Error(err)
}

func (s *BillServiceTest) TestFinish() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)
	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	_, err = s.srv.Processed(s.ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	_, err = s.srv.Finish(s.ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)

	found, err := s.srv.FindByID(s.ctx, created.ID)
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

	_, err := s.srv.Create(s.ctx, invalidBill)
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

	_, err = s.srv.Create(s.ctx, invalidBill2)
	s.Error(err)
	s.Contains(err.Error(), "总金额有误")
}

func (s *BillServiceTest) TestFindWithEmptyParam() {
	_, err := s.srv.FindByParam(s.ctx, nil)
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

	_, err := s.srv.Create(s.ctx, newBill)
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

	_, err := s.srv.Update(s.ctx, newBill)
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

	_, err := s.srv.Update(s.ctx, newBill)
	s.Error(err)
	s.Contains(err.Error(), "单据不存在")
}

func (s *BillServiceTest) TestFindNonExistentID() {
	_, err := s.srv.FindByID(s.ctx, 999999)
	s.Error(err)
	s.Contains(err.Error(), "单据不存在")
}

func (s *BillServiceTest) TestDeleteNonCreatedState() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.srv.Delete(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非创建状态不能删除")
}

func (s *BillServiceTest) TestProcessWithEmptyIDs() {
	res, err := s.srv.Process(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestProcessNonCreatedState() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非创建状态的单据不能处理")
}

func (s *BillServiceTest) TestProcessedNonProcessingState() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	res, err := s.srv.Processed(s.ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理中状态的单据不能处理完成")
}

func (s *BillServiceTest) TestFinishNonProcessedState() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)
	res, err := s.srv.Finish(s.ctx, []uint{created.ID}, new(time.Now()))
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理完成状态的单据不能完成")
}

func (s *BillServiceTest) TestDeleteWithEmptyIDs() {
	res, err := s.srv.Delete(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestProcessedWithEmptyIDs() {
	res, err := s.srv.Processed(s.ctx, []uint{}, nil)
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestFinishWithEmptyIDs() {
	res, err := s.srv.Finish(s.ctx, []uint{}, nil)
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

// TestCancelProcess tests CancelProcess business logic
func (s *BillServiceTest) TestCancelProcessWithEmptyIDs() {
	res, err := s.srv.CancelProcess(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestCancelProcessNonExistentBill() {
	res, err := s.srv.CancelProcess(s.ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BillServiceTest) TestCancelProcessNonProcessingState() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	res, err := s.srv.CancelProcess(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理中状态的单据不能取消处理")
}

func (s *BillServiceTest) TestCancelProcessSuccess() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	res, err := s.srv.CancelProcess(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Created, found.State)
	// ProcessedDate should be nil after cancel
	s.Nil(found.ProcessedDate)
}

// TestCancelProcessed tests CancelProcessed business logic
func (s *BillServiceTest) TestCancelProcessedWithEmptyIDs() {
	res, err := s.srv.CancelProcessed(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestCancelProcessedNonExistentBill() {
	res, err := s.srv.CancelProcessed(s.ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BillServiceTest) TestCancelProcessedNonProcessedState() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	res, err := s.srv.CancelProcessed(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非处理完成状态的单据不能取消处理完成")
}

func (s *BillServiceTest) TestCancelProcessedSuccess() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.srv.Processed(s.ctx, []uint{created.ID}, now)
	s.NoError(err)

	res, err := s.srv.CancelProcessed(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Processing, found.State)
	// ProcessedDate should be nil after cancel
	s.Nil(found.ProcessedDate)
}

// TestCancelFinish tests CancelFinish business logic
func (s *BillServiceTest) TestCancelFinishWithEmptyIDs() {
	res, err := s.srv.CancelFinish(s.ctx, []uint{})
	s.Error(err)
	s.Contains(err.Error(), "ID 为空")
	s.Nil(res)
}

func (s *BillServiceTest) TestCancelFinishNonExistentBill() {
	res, err := s.srv.CancelFinish(s.ctx, []uint{999999})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "单据不存在")
}

func (s *BillServiceTest) TestCancelFinishNonFinishedState() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.srv.Processed(s.ctx, []uint{created.ID}, now)
	s.NoError(err)

	res, err := s.srv.CancelFinish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(0, res.SuccessCount)
	s.Equal(1, res.FailCount)

	s.Len(res.Results, 1)
	s.False(res.Results[0].Success)
	s.Contains(res.Results[0].Message, "非完成状态的单据不能取消完成")
}

func (s *BillServiceTest) TestCancelFinishSuccess() {
	created, err := s.srv.Create(s.ctx, s.testBill)
	s.NoError(err)

	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	now := new(time.Time)
	_, err = s.srv.Processed(s.ctx, []uint{created.ID}, now)
	s.NoError(err)

	finishTime := new(time.Time)
	_, err = s.srv.Finish(s.ctx, []uint{created.ID}, finishTime)
	s.NoError(err)

	res, err := s.srv.CancelFinish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.NotNil(res)
	s.Equal(1, res.SuccessCount)
	s.Equal(0, res.FailCount)

	s.Len(res.Results, 1)
	s.True(res.Results[0].Success)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(bill.Processed, found.State)
	// FinishedDate should be nil after cancel
	s.Nil(found.FinishedDate)
}

// BillStatServiceTest 服务单统计服务集成测试
type BillStatServiceTest struct {
	suite.Suite
	ctx     context.Context
	srv     *bill.Service
	statSrv *bill.StatisticService
}

func TestBillStatService(t *testing.T) {
	suite.Run(t, new(BillStatServiceTest))
}

func (s *BillStatServiceTest) SetupSuite() {
	attachSvc, _ := attach.SetupForTest(test_util.AppCtx())
	s.srv, s.statSrv = bill.SetupForTest(test_util.AppCtx(), attachSvc)
}

func (s *BillStatServiceTest) SetupTest() {
	s.ctx = test_util.BeginTx(s.T())
	// 月度金额统计带缓存，逐个用例前清空，避免脏数据串扰
	test_util.AppCtx().Cache.Clear()
}

func (s *BillStatServiceTest) TestCountBillsByState() {
	createdCount, processingCount := 2, 1

	before, err := s.statSrv.CountBillsByState(s.ctx)
	s.NoError(err)

	for range createdCount {
		_, err := s.srv.Create(s.ctx, s.newBill(1000))
		s.NoError(err)
	}
	for range processingCount {
		billDTO, err := s.srv.Create(s.ctx, s.newBill(2000))
		s.NoError(err)
		_, err = s.srv.Process(s.ctx, []uint{billDTO.ID})
		s.NoError(err)
	}

	after, err := s.statSrv.CountBillsByState(s.ctx)
	s.NoError(err)
	s.Equal((*before)["CREATED"]+createdCount, (*after)["CREATED"])
	s.Equal((*before)["PROCESSING"]+processingCount, (*after)["PROCESSING"])
	s.Equal((*before)["PROCESSED"], (*after)["PROCESSED"])
}

func (s *BillStatServiceTest) TestSumAmountByMonth() {
	now := time.Now()
	otherMonth := now.AddDate(0, 0, -40)

	repo := bill.NewRepository(test_util.AppCtx().Db)
	before, err := repo.SumReceiveAmountByMonth(s.ctx)
	s.NoError(err)
	beforeMap := monthSumToMap(before)

	// 两张处理完成单据，分别落在当前月与更早的月份，金额各为 1000 / 2000
	s.createProcessedBill(1000, now)
	s.createProcessedBill(2000, otherMonth)

	after, err := repo.SumReceiveAmountByMonth(s.ctx)
	s.NoError(err)
	afterMap := monthSumToMap(after)

	// 新增金额按所在月份分布，两个月的正向增量应恰好为 1000 与 2000
	s.Equal(3000.0, positiveDeltaSum(beforeMap, afterMap))
	s.Equal(2, positiveDeltaCount(beforeMap, afterMap))

	// 统计服务（带缓存）与仓库结果一致，且重复调用命中缓存返回相同结果
	serviceResult, err := s.statSrv.SumAmountByMonth(s.ctx)
	s.NoError(err)
	s.Equal(sumMonthAmounts(after), sumMonthAmounts(serviceResult))

	cachedResult, err := s.statSrv.SumAmountByMonth(s.ctx)
	s.NoError(err)
	s.Equal(sumMonthAmounts(serviceResult), sumMonthAmounts(cachedResult))
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
	billDTO, err := s.srv.Create(s.ctx, s.newBill(total))
	s.Require().NoError(err)
	_, err = s.srv.Process(s.ctx, []uint{billDTO.ID})
	s.Require().NoError(err)
	date := processedDate
	_, err = s.srv.Processed(s.ctx, []uint{billDTO.ID}, &date)
	s.Require().NoError(err)
	return billDTO
}

func sumMonthAmounts(rows []result.MonthSumAmount) float64 {
	total := 0.0
	for _, row := range rows {
		total += row.Amount
	}
	return total
}

func monthSumToMap(rows []result.MonthSumAmount) map[string]float64 {
	m := make(map[string]float64, len(rows))
	for _, row := range rows {
		m[row.Month] = row.Amount
	}
	return m
}

func positiveDeltaSum(before, after map[string]float64) float64 {
	delta := 0.0
	for month, amount := range after {
		if amount > before[month] {
			delta += amount - before[month]
		}
	}
	return delta
}

func positiveDeltaCount(before, after map[string]float64) int {
	count := 0
	for month, amount := range after {
		if amount > before[month] {
			count++
		}
	}
	return count
}

// BillAttachTest 服务单↔附件关联测试
type BillAttachTest struct {
	suite.Suite
	ctx       context.Context
	attachSrv *attach.Service
	srv       *bill.Service
	relRepo   *bill.AttachRelRepo
}

func TestBillAttach(t *testing.T) {
	suite.Run(t, new(BillAttachTest))
}

func (s *BillAttachTest) SetupSuite() {
	appCtx := test_util.AppCtx()
	attachSvc, _ := attach.SetupForTest(appCtx)
	s.attachSrv = attachSvc
	s.srv, _ = bill.SetupForTest(appCtx, attachSvc)
	s.relRepo = bill.NewAttachRelRepo(appCtx.Db)
}

func (s *BillAttachTest) SetupTest() {
	s.ctx = test_util.BeginTx(s.T())
}

func (s *BillAttachTest) upload(name string) attach.DTO {
	headers, err := test_util.UploadFileHeaders(name, "attach content")
	s.Require().NoError(err)
	attaches, err := s.attachSrv.Upload(s.ctx, bill.AttachNamespace, headers)
	s.Require().NoError(err)
	s.Require().Len(attaches, 1)
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

	created, err := s.srv.Create(s.ctx, s.newBillDTO(attachment))
	s.Require().NoError(err)
	s.Require().Len(created.Attachments, 1)

	rels, err := s.relRepo.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Require().Len(rels, 1)
	s.Equal(uint(created.Attachments[0].ID), rels[0].AttachId)
	s.True(strings.HasPrefix(rels[0].Attach.RelativePath, bill.AttachNamespace+"/"))
	s.Equal(attach.StatusPersistent, rels[0].Attach.Status)

	created.Attachments = nil
	updated, err := s.srv.Update(s.ctx, created)
	s.Require().NoError(err)
	s.Empty(updated.Attachments)

	rels, err = s.relRepo.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Empty(rels)

	remaining, err := attach.NewRepository(test_util.AppCtx().Db).FindByID(s.ctx, uint(attachment.ID))
	s.Require().NoError(err)
	s.Nil(remaining)
}

// TestAttachRelationOnDelete 删除服务单时清理关联记录
func (s *BillAttachTest) TestAttachRelationOnDelete() {
	created, err := s.srv.Create(s.ctx, s.newBillDTO(s.upload("服务单附件-删除.txt")))
	s.Require().NoError(err)

	rels, err := s.relRepo.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Require().Len(rels, 1)

	_, err = s.srv.Delete(s.ctx, []uint{created.ID})
	s.Require().NoError(err)

	rels, err = s.relRepo.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Empty(rels)
}

// TestCommittedAttachCannotBeReused 已提交的附件不能被另一张单据复用
func (s *BillAttachTest) TestCommittedAttachCannotBeReused() {
	created, err := s.srv.Create(s.ctx, s.newBillDTO(s.upload("已提交附件.txt")))
	s.Require().NoError(err)
	s.Require().Len(created.Attachments, 1)

	_, err = s.srv.Create(s.ctx, s.newBillDTO(created.Attachments[0]))
	s.Require().Error(err)
	s.Contains(err.Error(), "附件不存在或已被提交")
}
