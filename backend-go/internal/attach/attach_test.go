package attach_test

import (
	"backend-go/internal/attach"
	"backend-go/internal/test"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

const testNamespace = "TEST_ATTACH"

// ServiceTest 附件服务测试
type ServiceTest struct {
	test.BaseSuite
	srv *attach.Service
}

func TestService(t *testing.T) {
	suite.Run(t, new(ServiceTest))
}

func (s *ServiceTest) SetupSuite() {
	s.BaseSuite.SetupSuite()
	s.srv = s.App.Attach
}

func (s *ServiceTest) upload(name string) attach.DTO {
	headers, err := s.UploadFileHeaders(name, "attach content")
	s.NoError(err)
	attaches, err := s.srv.Upload(s.Ctx, testNamespace, headers)
	s.NoError(err)
	s.Len(attaches, 1)
	return attaches[0]
}

func (s *ServiceTest) TestValidName() {
	valid := []string{"a.pdf", "合同 1.xlsx", "...", "a..b", "."}
	for _, name := range valid {
		s.NoError(attach.ValidName(name))
	}

	invalid := []string{"", "a/b", `a\b`, "..", "a:b", "a?b", "a*b", "a|b", "a<b", "a>b", `a"b`}
	for _, name := range invalid {
		s.Error(attach.ValidName(name))
	}
}

// TestUploadPersistsTransient 上传即落库，key 位于命名空间下，状态为瞬时态
func (s *ServiceTest) TestUploadPersistsTransient() {
	uploaded := s.upload("附件.txt")
	s.Positive(uploaded.ID)

	attachment, err := s.srv.FindById(s.Ctx, uploaded.ID)
	s.NoError(err)
	s.NotNil(attachment)
}

// TestPersist 只能提交一次
func (s *ServiceTest) TestPersist() {
	uploaded := s.upload("待提交附件.txt")
	ids := []uint{uint(uploaded.ID)}

	s.NoError(s.srv.Persist(s.Ctx, ids))

	attachment, err := s.srv.FindById(s.Ctx, ids[0])
	s.NoError(err)
	s.NotNil(attachment)

	s.Error(s.srv.Persist(s.Ctx, ids))
}

// TestDelete 删除附件及其物理文件
func (s *ServiceTest) TestDelete() {
	uploaded := s.upload("待删除附件.txt")
	ids := []uint{uploaded.ID}

	path, err := s.srv.GetAbsolutePathById(s.Ctx, uploaded.ID)
	s.NoError(err)

	s.NoError(s.srv.Delete(s.Ctx, ids))

	remaining, err := s.srv.FindById(s.Ctx, ids[0])
	s.NoError(err)
	s.Nil(remaining)
	s.False(attach.Exists(path))
}

// TestCleanTransientFiles 过期游离附件按状态回收
func (s *ServiceTest) TestCleanTransientFiles() {
	uploaded := s.upload("过期游离附件.txt")

	s.NoError(s.Tx.Exec("update attachment set created_date = ? where id = ?", time.Now().Add(-8*24*time.Hour), uploaded.ID).Error)

	s.NoError(s.srv.CleanTransientFiles(s.Ctx))

	remaining, err := s.srv.FindById(s.Ctx, uploaded.ID)
	s.NoError(err)
	s.Nil(remaining)
}
