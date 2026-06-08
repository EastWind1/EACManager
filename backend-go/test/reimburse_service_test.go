package test

import (
	"backend-go/internal/attach"
	"backend-go/internal/reimburse"
	"backend-go/pkg/result"
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

// ReimburseServiceTest 报销服务测试
type ReimburseServiceTest struct {
	*BaseServiceTest
	srv      *reimburse.Service
	testReim *reimburse.DTO
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
	s.testReim = &reimburse.DTO{
		Summary:       "测试报销",
		TotalAmount:   1000.00,
		ReimburseDate: new(time.Now()),
		Remark:        "测试备注",
		Details: []reimburse.DetailDTO{
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
	s.Equal(reimburse.Created, created.State)
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
		reim := &reimburse.DTO{
			Summary:       "测试报销" + strconv.Itoa(i),
			TotalAmount:   1000.00,
			ReimburseDate: new(time.Time),
			Remark:        "测试备注" + strconv.Itoa(i),
			Details: []reimburse.DetailDTO{
				{
					Name:   "明细项目" + strconv.Itoa(i),
					Amount: 1000.00,
				},
			},
		}
		_, err := s.srv.Create(s.ctx, reim)
		s.NoError(err)
	}

	queryParam := &reimburse.QueryParam{
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

	deleteResult, err := s.srv.Delete(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, deleteResult.SuccessCount)
	s.Equal(0, deleteResult.FailCount)

	_, err = s.srv.FindByID(s.ctx, created.ID)
	s.Error(err)
}

func (s *ReimburseServiceTest) TestProcess() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	processResult, err := s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, processResult.SuccessCount)
	s.Equal(0, processResult.FailCount)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(reimburse.Processing, found.State)
}

func (s *ReimburseServiceTest) TestFinish() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	processResult, err := s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, processResult.SuccessCount)
	s.Equal(0, processResult.FailCount)

	finishResult, err := s.srv.Finish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, finishResult.SuccessCount)
	s.Equal(0, finishResult.FailCount)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(reimburse.Finished, found.State)
}

func (s *ReimburseServiceTest) TestCancelProcess() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)
	s.Equal(reimburse.Created, created.State)

	// 提交到处理状态
	processResult, err := s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, processResult.SuccessCount)

	// 验证状态为处理中
	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(reimburse.Processing, found.State)

	// 取消处理（处理中 -> 新建）
	cancelProcessResult, err := s.srv.CancelProcess(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, cancelProcessResult.SuccessCount)
	s.Equal(0, cancelProcessResult.FailCount)

	// 验证状态回退到新建
	found, err = s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(reimburse.Created, found.State)
}

func (s *ReimburseServiceTest) TestCancelProcessInvalidState() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	// 创建状态不能取消处理
	cancelProcessResult, err := s.srv.CancelProcess(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(0, cancelProcessResult.SuccessCount)
	s.Equal(1, cancelProcessResult.FailCount)
	s.Contains(cancelProcessResult.Results[0].Message, "非处理中状态不能取消")
}

func (s *ReimburseServiceTest) TestCancelProcessed() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	// 提交到处理状态
	processResult, err := s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, processResult.SuccessCount)

	// 完成到完成状态
	finishResult, err := s.srv.Finish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, finishResult.SuccessCount)

	// 验证状态为完成
	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(reimburse.Finished, found.State)

	// 取消完成（完成 -> 处理中）
	cancelFinishResult, err := s.srv.CancelFinish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, cancelFinishResult.SuccessCount)
	s.Equal(0, cancelFinishResult.FailCount)

	// 验证状态回退到处理中
	found, err = s.srv.FindByID(s.ctx, created.ID)
	s.NoError(err)
	s.Equal(reimburse.Processing, found.State)
}

func (s *ReimburseServiceTest) TestCancelProcessedInvalidState() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	// 创建状态不能取消完成
	cancelFinishResult, err := s.srv.CancelFinish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(0, cancelFinishResult.SuccessCount)
	s.Equal(1, cancelFinishResult.FailCount)
	s.Contains(cancelFinishResult.Results[0].Message, "非完成状态不能取消")

	// 处理中状态也不能取消完成
	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	cancelFinishResult2, err := s.srv.CancelFinish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(0, cancelFinishResult2.SuccessCount)
	s.Equal(1, cancelFinishResult2.FailCount)
	s.Contains(cancelFinishResult2.Results[0].Message, "非完成状态不能取消")
}

func (s *ReimburseServiceTest) TestCancelEmptyIDs() {
	_, err := s.srv.CancelProcess(s.ctx, []uint{})
	s.Error(err)

	_, err = s.srv.CancelFinish(s.ctx, []uint{})
	s.Error(err)
}

func (s *ReimburseServiceTest) TestFindNonExistentRecord() {
	_, err := s.srv.FindByID(s.ctx, 999999) // 假设这个ID不存在
	s.Error(err)
}

func (s *ReimburseServiceTest) TestUpdateWithInvalidID() {
	invalidReim := &reimburse.DTO{
		ID:      0,
		Summary: "无效ID的更新",
	}
	_, err := s.srv.Update(s.ctx, invalidReim)
	s.Error(err)
}

func (s *ReimburseServiceTest) TestDeleteInvalidState() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	// 先提交，使其变为处理中状态
	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)

	// 尝试删除非创建状态的记录
	deleteResult, err := s.srv.Delete(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(0, deleteResult.SuccessCount)
	s.Equal(1, deleteResult.FailCount)
	s.Equal(1, len(deleteResult.Results))
	s.False(deleteResult.Results[0].Success)
	s.Contains(deleteResult.Results[0].Message, "非创建状态不能删除")
}

func (s *ReimburseServiceTest) TestInvalidStateTransitions() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	// 测试从创建直接到完成（跳过处理状态）
	finishResult1, err := s.srv.Finish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(0, finishResult1.SuccessCount)
	s.Equal(1, finishResult1.FailCount)
	s.Contains(finishResult1.Results[0].Message, "非处理状态不能完成")

	// 提交到处理状态
	processResult, err := s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, processResult.SuccessCount)
	s.Equal(0, processResult.FailCount)

	// 再次尝试提交（已经是处理状态）
	processResult2, err := s.srv.Process(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(0, processResult2.SuccessCount)
	s.Equal(1, processResult2.FailCount)
	s.Contains(processResult2.Results[0].Message, "非创建状态不能提交")

	// 完成后再次完成
	finishResult2, err := s.srv.Finish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(1, finishResult2.SuccessCount)
	s.Equal(0, finishResult2.FailCount)

	// 尝试从完成状态再次完成
	finishResult3, err := s.srv.Finish(s.ctx, []uint{created.ID})
	s.NoError(err)
	s.Equal(0, finishResult3.SuccessCount)
	s.Equal(1, finishResult3.FailCount)
	s.Contains(finishResult3.Results[0].Message, "非处理状态不能完成")
}

func (s *ReimburseServiceTest) TestDeleteEmptyIDs() {
	_, err := s.srv.Delete(s.ctx, []uint{})
	s.Error(err)
}

func (s *ReimburseServiceTest) TestProcessEmptyIDs() {
	_, err := s.srv.Process(s.ctx, []uint{})
	s.Error(err)
}

func (s *ReimburseServiceTest) TestFinishEmptyIDs() {
	_, err := s.srv.Finish(s.ctx, []uint{})
	s.Error(err)
}

func (s *ReimburseServiceTest) TestExportEmptyIDs() {
	_, err := s.srv.Export(s.ctx, []uint{})
	s.Error(err)
}

func (s *ReimburseServiceTest) TestExportNonExistentRecords() {
	_, err := s.srv.Export(s.ctx, []uint{999999})
	s.Error(err)
}

func (s *ReimburseServiceTest) TestFindByParamWithInvalidParam() {
	_, err := s.srv.FindByParam(s.ctx, nil)
	s.Error(err)
}

func (s *ReimburseServiceTest) TestExport() {
	created, err := s.srv.Create(s.ctx, s.testReim)
	s.NoError(err)

	path, err := s.srv.Export(s.ctx, []uint{created.ID})
	s.NoError(err)

	s.NotEmpty(path)
}
