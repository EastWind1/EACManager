package attach_test

import (
	"backend-go/internal/attach"
	"backend-go/internal/test_util"
	"context"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

const testNamespace = "TEST_ATTACH"

// ServiceTest 附件服务测试
type ServiceTest struct {
	suite.Suite
	ctx context.Context
	srv *attach.Service
}

func TestService(t *testing.T) {
	suite.Run(t, new(ServiceTest))
}

func (s *ServiceTest) SetupSuite() {
	s.srv, _ = attach.SetupForTest(test_util.AppCtx())
}

func (s *ServiceTest) SetupTest() {
	s.ctx = test_util.BeginTx(s.T())
}

func (s *ServiceTest) upload(name string) attach.DTO {
	headers, err := test_util.UploadFileHeaders(name, "attach content")
	s.Require().NoError(err)
	attaches, err := s.srv.Upload(s.ctx, testNamespace, headers)
	s.Require().NoError(err)
	s.Require().Len(attaches, 1)
	return attaches[0]
}

// TestUploadPersistsTransient 上传即落库，key 位于命名空间下，状态为瞬时态
func (s *ServiceTest) TestUploadPersistsTransient() {
	uploaded := s.upload("附件.txt")
	s.Positive(uploaded.ID)

	attachment, err := attach.NewRepository(test_util.AppCtx().Db).FindByID(s.ctx, uint(uploaded.ID))
	s.Require().NoError(err)
	s.Require().NotNil(attachment)
	s.Equal(attach.StatusTransient, attachment.Status)
	s.True(strings.HasPrefix(attachment.RelativePath, testNamespace+"/"))
}

// TestPersist 提交后转为持久态，且只能提交一次
func (s *ServiceTest) TestPersist() {
	uploaded := s.upload("待提交附件.txt")
	ids := []uint{uint(uploaded.ID)}

	s.Require().NoError(s.srv.Persist(s.ctx, ids))

	attachment, err := attach.NewRepository(test_util.AppCtx().Db).FindByID(s.ctx, ids[0])
	s.Require().NoError(err)
	s.Require().NotNil(attachment)
	s.Equal(attach.StatusPersistent, attachment.Status)

	s.Error(s.srv.Persist(s.ctx, ids))
}

// TestDelete 删除附件及其物理文件
func (s *ServiceTest) TestDelete() {
	uploaded := s.upload("待删除附件.txt")
	ids := []uint{uint(uploaded.ID)}

	path, err := s.srv.GetAbsolutePathById(s.ctx, uploaded.ID)
	s.Require().NoError(err)

	s.Require().NoError(s.srv.Delete(s.ctx, ids))

	remaining, err := attach.NewRepository(test_util.AppCtx().Db).FindByID(s.ctx, ids[0])
	s.Require().NoError(err)
	s.Nil(remaining)
	s.False(attach.Exists(path))
}

// TestCleanTransientFiles 过期游离附件按状态回收
func (s *ServiceTest) TestCleanTransientFiles() {
	uploaded := s.upload("过期游离附件.txt")

	s.Require().NoError(test_util.Tx(s.T(), s.ctx).
		Exec("update attachment set created_date = ? where id = ?", time.Now().Add(-8*24*time.Hour), uploaded.ID).
		Error)

	s.Require().NoError(s.srv.CleanTransientFiles(s.ctx))

	remaining, err := attach.NewRepository(test_util.AppCtx().Db).FindByID(s.ctx, uint(uploaded.ID))
	s.Require().NoError(err)
	s.Nil(remaining)
}
