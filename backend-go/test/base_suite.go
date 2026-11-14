// Package test_util 提供集成测试基类：共享应用、逐用例事务与上传辅助。
package test

import (
	"backend-go/config"
	"backend-go/internal/security"
	"backend-go/pkg/database"
	"backend-go/server"
	"bytes"
	"context"
	"errors"
	"mime/multipart"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync"

	"github.com/stretchr/testify/suite"
	"gorm.io/gorm"
)

var (
	appOnce sync.Once
	app     *server.App
	err     error
)

// BaseSuite 集成测试基类：SetupSuite 初始化应用，SetupTest 开启事务，TearDownTest 回滚
type BaseSuite struct {
	suite.Suite
	App *server.App
	Ctx context.Context
	Tx  *gorm.DB
}

func (s *BaseSuite) SetupSuite() {
	appOnce.Do(func() {
		if os.Getenv("JWT_SECRET") == "" {
			_ = os.Setenv("JWT_SECRET", "test-only-jwt-key-4f6c1d2a9b8e7c3d5a1f0e9d8c7b6a5f")
		}
		cfg := config.NewConfig()

		// 附件统一落到临时目录，避免在各模块目录下留下 attachment/
		if cfg.Attach != nil {
			cfg.Attach.Path = filepath.Join(os.TempDir(), "eac-test-attachment")
		}
		app, err = server.NewApp(cfg)
	})
	if err != nil {
		s.Require().NoError(err, "初始化测试应用失败")
	}
	s.App = app
}

func (s *BaseSuite) SetupTest() {
	s.Tx = s.App.DB.Begin()
	s.Require().NoError(s.Tx.Error, "开启测试事务失败")
	ctx := security.SetUserToCtx(s.T().Context(), &security.AuthorityUser{ID: 1, Username: "root", Password: "root"})
	s.Ctx = context.WithValue(ctx, database.TxKey{}, s.Tx)
}

func (s *BaseSuite) TearDownTest() {
	if s.Tx == nil {
		return
	}
	err := s.Tx.Rollback().Error
	s.Tx = nil
	if err != nil && !errors.Is(err, gorm.ErrInvalidTransaction) {
		s.Require().NoError(err, "回滚测试事务失败")
	}
}

// UploadFileHeaders 构造 multipart 上传文件
func (s *BaseSuite) UploadFileHeaders(name, content string) ([]*multipart.FileHeader, error) {
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, err := writer.CreateFormFile("files", name)
	if err != nil {
		return nil, err
	}
	if _, err = part.Write([]byte(content)); err != nil {
		return nil, err
	}
	if err = writer.Close(); err != nil {
		return nil, err
	}

	request := httptest.NewRequest("POST", "/", &buf)
	request.Header.Set("Content-Type", writer.FormDataContentType())
	if err = request.ParseMultipartForm(1 << 20); err != nil {
		return nil, err
	}
	return request.MultipartForm.File["files"], nil
}
