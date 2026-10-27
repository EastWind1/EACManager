package bill_test

import (
	"backend-go/internal/attach"
	"backend-go/internal/bill"
	"backend-go/internal/company"
	"backend-go/internal/test_util"
	"backend-go/pkg/result"
	"context"
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

const scopedCategory = "orm-refactor-scope"

// BillRegressionTest 业务回归用例（从明细对账、状态流转等真实缺陷沉淀）
type BillRegressionTest struct {
	suite.Suite
	ctx       context.Context
	srv       *bill.Service
	statSrv   *bill.StatisticService
	company   *company.Service
	attachSrv *attach.Service
}

func TestBillRegression(t *testing.T) {
	suite.Run(t, new(BillRegressionTest))
}

func (s *BillRegressionTest) SetupSuite() {
	appCtx := test_util.AppCtx()
	s.attachSrv, _ = attach.SetupForTest(appCtx)
	s.srv, s.statSrv = bill.SetupForTest(appCtx, s.attachSrv)
	s.company = company.SetupForTest(appCtx)
}

func (s *BillRegressionTest) SetupTest() {
	s.ctx = test_util.BeginTx(s.T())
}

func (s *BillRegressionTest) newBill(name string, details ...bill.DetailDTO) *bill.DTO {
	total := 0.0
	for _, detail := range details {
		total += detail.Subtotal
	}
	return &bill.DTO{
		ProjectName: scopedCategory + "-" + name,
		TotalAmount: total,
		OrderDate:   new(time.Now()),
		Details:     details,
	}
}

func detail(device string, unitPrice, quantity float64) bill.DetailDTO {
	return bill.DetailDTO{
		Device:    device,
		UnitPrice: unitPrice,
		Quantity:  quantity,
		Subtotal:  unitPrice * quantity,
	}
}

// TestUpdateKeepsDetailIDsAndRemovesOrphans 明细按 ID 对账：留下的行 ID 不变，删除的行真的消失，新增行插入
func (s *BillRegressionTest) TestUpdateKeepsDetailIDsAndRemovesOrphans() {
	created, err := s.srv.Create(s.ctx, s.newBill("明细对账",
		detail("设备A", 100, 1),
		detail("设备B", 200, 1),
	))
	s.Require().NoError(err)
	s.Require().Len(created.Details, 2)
	keptID := created.Details[0].ID

	// 保留第一条（改单价）、删掉第二条、插入一条新的
	update := *created
	update.Details = []bill.DetailDTO{
		{ID: created.Details[0].ID, Device: "设备A-改", UnitPrice: 150, Quantity: 1, Subtotal: 150},
		detail("设备C", 50, 2),
	}
	update.TotalAmount = 250
	updated, err := s.srv.Update(s.ctx, &update)
	s.Require().NoError(err)
	s.Len(updated.Details, 2)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Require().Len(found.Details, 2)
	s.Equal(keptID, found.Details[0].ID)
	s.Equal("设备A-改", found.Details[0].Device)
	for _, d := range found.Details {
		s.NotEqual("设备B", d.Device)
	}
	s.NotZero(found.Details[1].ID)
	s.Equal("设备C", found.Details[1].Device)
}

// TestStateTransitionOnlyTouchesState 状态流转只写状态相关列，不回写旧快照
func (s *BillRegressionTest) TestStateTransitionOnlyTouchesState() {
	created, err := s.srv.Create(s.ctx, s.newBill("状态流转", detail("设备", 100, 1)))
	s.Require().NoError(err)

	_, err = s.srv.Process(s.ctx, []uint{created.ID})
	s.Require().NoError(err)

	res, err := s.srv.Processed(s.ctx, []uint{created.ID}, new(time.Now()))
	s.Require().NoError(err)
	s.Equal(1, res.SuccessCount)

	found, err := s.srv.FindByID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Equal(bill.Processed, found.State)
	s.NotNil(found.ProcessedDate)
	s.Len(found.Details, 1)
}

// TestFindByParamFallsBackToIdDesc 分页但无排序时按 id 倒序兜底
func (s *BillRegressionTest) TestFindByParamFallsBackToIdDesc() {
	for i := range 3 {
		_, err := s.srv.Create(s.ctx, s.newBill("兜底排序"+strconv.Itoa(i), detail("设备", 100, 1)))
		s.Require().NoError(err)
	}

	page, err := s.srv.FindByParam(s.ctx, &bill.QueryParam{
		ProjectName: scopedCategory + "-兜底排序",
		PageIndex:   new(0), PageSize: new(3),
	})
	s.Require().NoError(err)
	s.Require().Len(page.Items, 3)
	for i := 1; i < len(page.Items); i++ {
		s.Greater(page.Items[i-1].ID, page.Items[i].ID, "无排序分页应按 id 倒序")
	}

	// 显式排序优先于兜底
	asc, err := s.srv.FindByParam(s.ctx, &bill.QueryParam{
		ProjectName: scopedCategory + "-兜底排序",
		PageIndex:   new(0),
		PageSize:    new(3),
		Sorts:       []result.SortParam{{Field: "id", Direction: "asc"}},
	})
	s.Require().NoError(err)
	s.Require().Len(asc.Items, 3)
	s.Less(asc.Items[0].ID, asc.Items[1].ID, "显式排序应覆盖兜底")
}

// TestExportBatchesAttachmentsAndCompanies 导出用批量查询：附件与公司一次取回
func (s *BillRegressionTest) TestExportBatchesAttachmentsAndCompanies() {
	companyDTO, err := s.company.Create(s.ctx, &company.DTO{Name: scopedCategory + "-公司"})
	s.Require().NoError(err)

	headers, err := test_util.UploadFileHeaders("导出附件.txt", "attach content")
	s.Require().NoError(err)
	attaches, err := s.attachSrv.Upload(s.ctx, bill.AttachNamespace, headers)
	s.Require().NoError(err)

	first := s.newBill("导出A", detail("设备", 100, 1))
	first.ProductCompany = companyDTO
	first.Attachments = attaches
	created, err := s.srv.Create(s.ctx, first)
	s.Require().NoError(err)
	second, err := s.srv.Create(s.ctx, s.newBill("导出B", detail("设备", 200, 1)))
	s.Require().NoError(err)

	bills, err := bill.NewRepository(test_util.AppCtx().Db).FindAllWithDetails(s.ctx, []uint{created.ID, second.ID})
	s.Require().NoError(err)
	s.Require().Len(bills, 2)
	for _, b := range bills {
		s.Len(b.Details, 1)
		if b.ID == created.ID {
			s.Require().NotNil(b.ProductCompany)
			s.Equal(companyDTO.Name, b.ProductCompany.Name)
		}
	}

	relSrv := bill.NewAttachRelService(test_util.AppCtx().Db, s.attachSrv)
	rels, err := relSrv.FindByBillIDs(s.ctx, []uint{created.ID, second.ID})
	s.Require().NoError(err)
	s.Len(rels[created.ID], 1)
	s.Empty(rels[second.ID])
}
