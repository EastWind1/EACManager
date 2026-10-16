package test

import (
	"backend-go/internal/attach"
	"backend-go/internal/bill"
	"backend-go/internal/reimburse"
	"backend-go/pkg/database"
	"bytes"
	"mime/multipart"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
	"gorm.io/gorm"
)

// AttachRelationTest 单据附件关联测试
//
// 验证附件以 namespace/唯一id 存储、上传即落库、提交后不可复用，
// 以及单据与附件的关联由各业务模块自行维护。
type AttachRelationTest struct {
	*BaseServiceTest
	attachSrv *attach.Service
	bizSrv    *bill.BizService
	reimSrv   *reimburse.Service
	billRel   *bill.ServiceBillAttachRelRepo
	reimRel   *reimburse.AttachRelRepo
}

func NewAttachRelationTest() *AttachRelationTest {
	base := NewBaseServiceTest()
	attachSrv, contentSrv := attach.SetupForTest(base.appCtx)
	bizSrv, _ := bill.SetupForTest(base.appCtx, attachSrv)
	return &AttachRelationTest{
		BaseServiceTest: base,
		attachSrv:       attachSrv,
		bizSrv:          bizSrv,
		reimSrv:         reimburse.SetupForTest(base.appCtx, attachSrv, contentSrv),
		billRel:         bill.NewServiceBillAttachRelRepo(base.appCtx.Db),
		reimRel:         reimburse.NewReimburseAttachRelRepo(base.appCtx.Db),
	}
}

func TestAttachRelation(t *testing.T) {
	suite.Run(t, NewAttachRelationTest())
}

// uploadTempFile 构造一个上传的临时附件
func (s *AttachRelationTest) uploadTempFile(name string, content string) []*multipart.FileHeader {
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, err := writer.CreateFormFile("files", name)
	s.Require().NoError(err)
	_, err = part.Write([]byte(content))
	s.Require().NoError(err)
	s.Require().NoError(writer.Close())

	request := httptest.NewRequest("POST", "/", &buf)
	request.Header.Set("Content-Type", writer.FormDataContentType())
	s.Require().NoError(request.ParseMultipartForm(1 << 20))
	return request.MultipartForm.File["files"]
}

// upload 上传一个附件并返回 DTO
func (s *AttachRelationTest) upload(namespace string, name string) attach.AttachmentDTO {
	attaches, err := s.attachSrv.Upload(s.ctx, namespace, s.uploadTempFile(name, "attach content"))
	s.Require().NoError(err)
	s.Require().Len(attaches, 1)
	return attaches[0]
}

// tx 取测试事务，用于直接改库
func (s *AttachRelationTest) tx() *gorm.DB {
	tx, ok := s.ctx.Value(database.TxKey{}).(*gorm.DB)
	s.Require().True(ok)
	return tx
}

func (s *AttachRelationTest) newServiceBillDTO(attachments ...attach.AttachmentDTO) *bill.ServiceBillDTO {
	return &bill.ServiceBillDTO{
		ProjectName: "附件关联测试项目",
		TotalAmount: 100.00,
		OrderDate:   new(time.Now()),
		Attachments: attachments,
		Details: []bill.ServiceBillDetailDTO{
			{
				Device:    "测试设备",
				UnitPrice: 100.00,
				Quantity:  1,
				Subtotal:  100.00,
			},
		},
	}
}

func (s *AttachRelationTest) newReimburseDTO(attachments ...attach.AttachmentDTO) *reimburse.DTO {
	return &reimburse.DTO{
		Summary:       "附件关联测试报销",
		TotalAmount:   100.00,
		ReimburseDate: new(time.Now()),
		Attachments:   attachments,
		Details: []reimburse.DetailDTO{
			{
				Name:   "测试明细",
				Amount: 100.00,
			},
		},
	}
}

// TestServiceBillRelation 服务单附件关联由服务单模块维护
func (s *AttachRelationTest) TestServiceBillRelation() {
	attachment := s.upload(bill.AttachNamespace, "服务单附件.txt")
	s.Positive(attachment.ID)

	// 上传即落库，状态为瞬时态
	uploaded, err := attach.NewRepository(s.appCtx.Db).FindByID(s.ctx, uint(attachment.ID))
	s.Require().NoError(err)
	s.Require().NotNil(uploaded)
	s.Equal(attach.StatusTransient, uploaded.Status)

	created, err := s.bizSrv.Create(s.ctx, s.newServiceBillDTO(attachment))
	s.Require().NoError(err)
	s.Require().Len(created.Attachments, 1)

	rels, err := s.billRel.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Require().Len(rels, 1)
	s.Equal(uint(created.Attachments[0].ID), rels[0].AttachId)
	// key 为 namespace/唯一id，不再包含单据编号
	prefix := bill.AttachNamespace + "/"
	s.True(strings.HasPrefix(rels[0].Attach.RelativePath, prefix))
	s.Equal(attach.StatusPersistent, rels[0].Attach.Status)

	// 移除附件后关联记录与附件记录一并清理
	created.Attachments = nil
	updated, err := s.bizSrv.Update(s.ctx, created)
	s.Require().NoError(err)
	s.Empty(updated.Attachments)

	rels, err = s.billRel.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Empty(rels)

	repo := attach.NewRepository(s.appCtx.Db)
	remaining, err := repo.FindByID(s.ctx, uint(attachment.ID))
	s.Require().NoError(err)
	s.Nil(remaining)
}

// TestServiceBillRelationOnDelete 删除服务单时清理关联记录
func (s *AttachRelationTest) TestServiceBillRelationOnDelete() {
	created, err := s.bizSrv.Create(s.ctx, s.newServiceBillDTO(s.upload(bill.AttachNamespace, "服务单附件-删除.txt")))
	s.Require().NoError(err)

	rels, err := s.billRel.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Require().Len(rels, 1)

	_, err = s.bizSrv.Delete(s.ctx, []uint{created.ID})
	s.Require().NoError(err)

	rels, err = s.billRel.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Empty(rels)
}

// TestReimburseRelation 报销单附件关联由报销单模块维护
func (s *AttachRelationTest) TestReimburseRelation() {
	created, err := s.reimSrv.Create(s.ctx, s.newReimburseDTO(s.upload(reimburse.AttachNamespace, "报销单附件.txt")))
	s.Require().NoError(err)
	s.Require().Len(created.Attachments, 1)

	rels, err := s.reimRel.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Require().Len(rels, 1)
	s.Equal(uint(created.Attachments[0].ID), rels[0].AttachId)
	prefix := reimburse.AttachNamespace + "/"
	s.True(strings.HasPrefix(rels[0].Attach.RelativePath, prefix))

	// 删除报销单后关联记录一并清理
	res, err := s.reimSrv.Delete(s.ctx, []uint{created.ID})
	s.Require().NoError(err)
	s.Equal(1, res.SuccessCount)

	rels, err = s.reimRel.FindByBillID(s.ctx, created.ID)
	s.Require().NoError(err)
	s.Empty(rels)
}

// TestCommittedAttachmentCannotBeReused 已提交的附件不能被另一张单据复用
func (s *AttachRelationTest) TestCommittedAttachmentCannotBeReused() {
	attachment := s.upload(bill.AttachNamespace, "已提交附件.txt")
	created, err := s.bizSrv.Create(s.ctx, s.newServiceBillDTO(attachment))
	s.Require().NoError(err)
	s.Require().Len(created.Attachments, 1)

	// 同一附件再挂到另一张单据上应被拒绝
	_, err = s.bizSrv.Create(s.ctx, s.newServiceBillDTO(created.Attachments[0]))
	s.Require().Error(err)
	s.Contains(err.Error(), "附件不存在或已被提交")
}

// TestCleanTransientFiles 过期游离附件按状态回收
func (s *AttachRelationTest) TestCleanTransientFiles() {
	attachment := s.upload(bill.AttachNamespace, "过期游离附件.txt")

	// 回退创建时间，模拟超过保留期
	s.Require().NoError(s.tx().
		Exec("update attachment set created_date = ? where id = ?", time.Now().Add(-8*24*time.Hour), attachment.ID).
		Error)

	s.Require().NoError(s.attachSrv.CleanTransientFiles(s.ctx))

	repo := attach.NewRepository(s.appCtx.Db)
	remaining, err := repo.FindByID(s.ctx, uint(attachment.ID))
	s.Require().NoError(err)
	s.Nil(remaining)
}
