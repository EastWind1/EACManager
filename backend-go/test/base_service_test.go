package test

import (
	"backend-go/config"
	"backend-go/internal/module/user/model"
	"backend-go/internal/pkg/auth"
	"backend-go/internal/pkg/cache"
	appContext "backend-go/internal/pkg/context"
	"backend-go/internal/pkg/database"
	"backend-go/internal/pkg/logger"
	"context"

	"github.com/stretchr/testify/suite"
	"gorm.io/gorm"
)

// BaseServiceTest 基础测试服务，用户初始化测试环境
type BaseServiceTest struct {
	suite.Suite
	ctx    context.Context
	appCtx *appContext.AppContext
}

func NewBaseServiceTest() *BaseServiceTest {
	appCtx := new(appContext.AppContext)
	cfg := config.NewConfig()
	appCtx.Cfg = cfg
	logger.InitLogger(cfg.Log)
	appCtx.Cache = cache.NewInMemoryCache(cfg.Cache)
	ctx := context.WithValue(context.Background(), auth.CurUserKey{}, new(model.User{
		ID:        1,
		Username:  "root",
		Password:  "root",
		Name:      "root",
		Authority: "ROLE_ADMIN",
	}))
	appCtx.Db = database.NewDB(cfg.Db, cfg.Log).WithContext(ctx)
	ctx = appCtx.Db.Statement.Context
	return &BaseServiceTest{
		ctx:    ctx,
		appCtx: appCtx,
	}
}

// SetupTest 启动事务
func (suite *BaseServiceTest) SetupTest() {
	tx := suite.appCtx.Db.Begin()
	suite.ctx = context.WithValue(suite.ctx, database.TxKey{}, tx)
}

// TearDownTest 回滚所有操作
func (suite *BaseServiceTest) TearDownTest() {
	suite.ctx.Value(database.TxKey{}).(*gorm.DB).Rollback()
}
