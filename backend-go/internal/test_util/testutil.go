// Package test_util 提供集成测试共用的测试环境与事务隔离。
package test_util

import (
	"backend-go/config"
	"backend-go/internal/user"
	"backend-go/pkg/auth"
	"backend-go/pkg/cache"
	appContext "backend-go/pkg/context"
	"backend-go/pkg/database"
	"backend-go/pkg/logger"
	"bytes"
	"context"
	"errors"
	"mime/multipart"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync"
	"testing"

	"gorm.io/gorm"
)

var (
	appCtxOnce sync.Once
	appCtx     *appContext.AppContext
	initErr    error
)

// AppCtx 返回本包共享的应用上下文，首次调用时初始化
func AppCtx() *appContext.AppContext {
	initAppCtx()
	if initErr != nil {
		panic(initErr)
	}
	return appCtx
}

// BeginTx 开启测试事务并返回携带它的 context（仓储从 context 取事务），用例结束时自动回滚
func BeginTx(t *testing.T) context.Context {
	t.Helper()
	db := AppCtx().Db
	tx := db.Begin()
	if tx.Error != nil {
		t.Fatalf("开启测试事务失败: %v", tx.Error)
	}
	t.Cleanup(func() {
		if err := tx.Rollback().Error; err != nil && !errors.Is(err, gorm.ErrInvalidTransaction) {
			t.Logf("回滚测试事务失败: %v", err)
		}
	})
	return context.WithValue(db.Statement.Context, database.TxKey{}, tx)
}

// Tx 返回 ctx 携带的测试事务，供用例执行原始 SQL
func Tx(t *testing.T, ctx context.Context) *gorm.DB {
	t.Helper()
	tx, ok := ctx.Value(database.TxKey{}).(*gorm.DB)
	if !ok {
		t.Fatalf("当前 context 未携带测试事务，请用 BeginTx 创建")
	}
	return tx
}

// UploadFileHeaders 构造 multipart 上传文件
func UploadFileHeaders(name, content string) ([]*multipart.FileHeader, error) {
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

func initAppCtx() {
	appCtxOnce.Do(func() {
		cfg := config.NewConfig()
		logger.InitLogger(cfg.Log)

		// 附件统一落到临时目录，避免在各模块目录下留下 attachment/
		if cfg.Attach != nil {
			cfg.Attach.Path = filepath.Join(os.TempDir(), "eac-test-attachment")
		}

		ctx := context.WithValue(context.Background(), auth.CurUserKey{}, new(user.User{
			ID:       1,
			Username: "root",
			Password: "root",
			Name:     "root",
		}))
		db := database.NewDB(cfg.Db, cfg.Log).WithContext(ctx)

		appCtx = &appContext.AppContext{
			Cfg:   cfg,
			Cache: cache.NewInMemoryCache(cfg.Cache),
			Db:    db,
		}
	})
}
