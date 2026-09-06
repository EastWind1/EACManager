package test

import (
	"backend-go/internal/attach"
	"backend-go/internal/reimburse"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

// ReimburseStatServiceTest 报销单统计服务集成测试
type ReimburseStatServiceTest struct {
	*BaseServiceTest
	srv     *reimburse.Service
	statSrv *reimburse.StatisticService
}

func NewReimburseStatServiceTest() *ReimburseStatServiceTest {
	base := NewBaseServiceTest()
	attachSrv, attachMapSrv := attach.SetupForTest(base.appCtx)
	return &ReimburseStatServiceTest{
		BaseServiceTest: base,
		srv:             reimburse.SetupForTest(base.appCtx, attachSrv, attachMapSrv),
		statSrv: reimburse.NewStatisticService(
			base.appCtx.Cache,
			reimburse.NewReimburseRepository(base.appCtx.Db),
		),
	}
}

func TestReimburseStatService(t *testing.T) {
	suite.Run(t, NewReimburseStatServiceTest())
}

func (s *ReimburseStatServiceTest) SetupTest() {
	s.BaseServiceTest.SetupTest()
	// 月度金额统计带缓存，逐个用例前清空，避免脏数据串扰
	s.appCtx.Cache.Clear()
}

func (s *ReimburseStatServiceTest) TestCountReimburseByState() {
	createdCount, processingCount, finishedCount := 2, 1, 1

	before, err := s.statSrv.CountReimburseByState(s.ctx)
	s.NoError(err)

	for range createdCount {
		_, err := s.srv.Create(s.ctx, s.newReimburseAt(1000, time.Now()))
		s.NoError(err)
	}
	for range processingCount {
		dto, err := s.srv.Create(s.ctx, s.newReimburseAt(2000, time.Now()))
		s.NoError(err)
		_, err = s.srv.Process(s.ctx, []uint{dto.ID})
		s.NoError(err)
	}
	for range finishedCount {
		dto, err := s.srv.Create(s.ctx, s.newReimburseAt(3000, time.Now()))
		s.NoError(err)
		_, err = s.srv.Process(s.ctx, []uint{dto.ID})
		s.NoError(err)
		_, err = s.srv.Finish(s.ctx, []uint{dto.ID})
		s.NoError(err)
	}

	after, err := s.statSrv.CountReimburseByState(s.ctx)
	s.NoError(err)
	s.Equal((*before)["CREATED"]+createdCount, (*after)["CREATED"])
	s.Equal((*before)["PROCESSING"]+processingCount, (*after)["PROCESSING"])
	s.Equal((*before)["FINISHED"]+finishedCount, (*after)["FINISHED"])
}

func (s *ReimburseStatServiceTest) TestSumAmountByMonth() {
	now := time.Now()
	otherMonth := now.AddDate(0, 0, -40)

	repo := reimburse.NewReimburseRepository(s.appCtx.Db)
	before, err := repo.SumAmountByMonth(s.ctx)
	s.NoError(err)
	beforeMap := monthSumToMap(before)

	// 两张报销单分别落在当前月与更早的月份，金额各为 1000 / 2000
	_, err = s.srv.Create(s.ctx, s.newReimburseAt(1000, now))
	s.NoError(err)
	_, err = s.srv.Create(s.ctx, s.newReimburseAt(2000, otherMonth))
	s.NoError(err)

	after, err := repo.SumAmountByMonth(s.ctx)
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

// newReimburseAt 构建金额合法的报销单，报销日期由调用方指定
func (s *ReimburseStatServiceTest) newReimburseAt(total float64, date time.Time) *reimburse.DTO {
	return &reimburse.DTO{
		Summary:       "统计测试报销",
		TotalAmount:   total,
		ReimburseDate: &date,
		Remark:        "统计测试备注",
		Details: []reimburse.DetailDTO{
			{
				Name:   "报销项目",
				Amount: total,
			},
		},
	}
}
