package test

import (
	"backend-go/internal/attach"
	"backend-go/internal/bill"
	"backend-go/pkg/result"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

// BillStatServiceTest 服务单统计服务集成测试
type BillStatServiceTest struct {
	*BaseServiceTest
	bizSrv  *bill.BizService
	statSrv *bill.StatisticService
}

func NewBillStatServiceTest() *BillStatServiceTest {
	base := NewBaseServiceTest()
	attachSrv, _ := attach.SetupForTest(base.appCtx)
	bizSrv, statSrv := bill.SetupForTest(base.appCtx, attachSrv)
	return &BillStatServiceTest{
		BaseServiceTest: base,
		bizSrv:          bizSrv,
		statSrv:         statSrv,
	}
}

func TestBillStatService(t *testing.T) {
	suite.Run(t, NewBillStatServiceTest())
}

func (s *BillStatServiceTest) SetupTest() {
	s.BaseServiceTest.SetupTest()
	// 月度金额统计带缓存，逐个用例前清空，避免脏数据串扰
	s.appCtx.Cache.Clear()
}

func (s *BillStatServiceTest) TestCountBillsByState() {
	createdCount, processingCount := 2, 1

	before, err := s.statSrv.CountBillsByState(s.ctx)
	s.NoError(err)

	for range createdCount {
		_, err := s.bizSrv.Create(s.ctx, s.newBill(1000))
		s.NoError(err)
	}
	for range processingCount {
		billDTO, err := s.bizSrv.Create(s.ctx, s.newBill(2000))
		s.NoError(err)
		_, err = s.bizSrv.Process(s.ctx, []uint{billDTO.ID})
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

	repo := bill.NewRepository(s.appCtx.Db)
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
func (s *BillStatServiceTest) newBill(total float64) *bill.ServiceBillDTO {
	return &bill.ServiceBillDTO{
		State:          bill.Created,
		ProjectName:    "统计测试项目",
		ProjectAddress: "测试地址",
		TotalAmount:    total,
		OrderDate:      new(time.Now()),
		Details: []bill.ServiceBillDetailDTO{
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
func (s *BillStatServiceTest) createProcessedBill(total float64, processedDate time.Time) *bill.ServiceBillDTO {
	billDTO, err := s.bizSrv.Create(s.ctx, s.newBill(total))
	s.Require().NoError(err)
	_, err = s.bizSrv.Process(s.ctx, []uint{billDTO.ID})
	s.Require().NoError(err)
	date := processedDate
	_, err = s.bizSrv.Processed(s.ctx, []uint{billDTO.ID}, &date)
	s.Require().NoError(err)
	return billDTO
}

// sumMonthAmounts 汇总月度金额
func sumMonthAmounts(rows []result.MonthSumAmount) float64 {
	total := 0.0
	for _, row := range rows {
		total += row.Amount
	}
	return total
}

// monthSumToMap 将月度金额列表转换为 month -> amount 映射
func monthSumToMap(rows []result.MonthSumAmount) map[string]float64 {
	m := make(map[string]float64, len(rows))
	for _, row := range rows {
		m[row.Month] = row.Amount
	}
	return m
}

// positiveDeltaSum 汇总 after 相比 before 各月份的正向增量
func positiveDeltaSum(before, after map[string]float64) float64 {
	delta := 0.0
	for month, amount := range after {
		if amount > before[month] {
			delta += amount - before[month]
		}
	}
	return delta
}

// positiveDeltaCount 统计存在正向增量的月份数
func positiveDeltaCount(before, after map[string]float64) int {
	count := 0
	for month, amount := range after {
		if amount > before[month] {
			count++
		}
	}
	return count
}
