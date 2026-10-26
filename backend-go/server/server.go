package server

import (
	"backend-go/config"
	"backend-go/internal/attach"
	"backend-go/internal/bill"
	"backend-go/internal/company"
	"backend-go/internal/reimburse"
	"backend-go/internal/security"
	"backend-go/internal/user"
	"backend-go/pkg/cache"
	"backend-go/pkg/context"
	"backend-go/pkg/database"
	"backend-go/pkg/logger"
	"backend-go/pkg/middleware"
	stdContext "context"
	"fmt"

	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/log"
	"github.com/gofiber/fiber/v3/middleware/cors"
	"github.com/gofiber/fiber/v3/middleware/recover"
)

// Run 启动服务
func Run() {
	// 初始化上下文
	ctx := new(context.AppContext)
	// 加载配置
	cfg := config.NewConfig()
	ctx.Cfg = cfg
	// 初始化服务
	server := fiber.New(fiber.Config{
		ErrorHandler: middleware.ErrorHandler(),
		BodyLimit:    int(cfg.Attach.MaxFileSize),
	})
	ctx.Server = server
	// 初始化日志
	logger.InitLogger(cfg.Log)
	server.Use(cors.New())
	// 初始化异常处理
	server.Use(recover.New(recover.Config{
		EnableStackTrace: true,
	}))
	// 初始化响应体包装
	server.Use(middleware.ResultWrap())
	// 初始化缓存
	ctx.Cache = cache.NewInMemoryCache(cfg.Cache)
	// 初始化数据库
	ctx.Db = database.NewDB(cfg.Db, cfg.Log)
	// 初始化路由
	router := server.Group("/api")
	// 初始化模块
	{
		// 先注册认证中间件，再注册依赖动作权限的模块
		permissionSrv := security.NewService(ctx)
		permissionSyncSrv := security.NewPermissionSyncService(ctx)
		securityInitializer := security.NewSecurityInitializer(ctx)

		user.Setup(ctx, router, permissionSrv, permissionSyncSrv)
		security.Setup(ctx, router, permissionSrv, permissionSyncSrv)
		companySrv := company.Setup(ctx, router, permissionSrv, permissionSyncSrv)
		attachSrv, contentSrv := attach.Setup(ctx, router, permissionSrv, permissionSyncSrv)
		reimburse.Setup(ctx, router, attachSrv, contentSrv, permissionSrv, permissionSyncSrv)
		bill.Setup(ctx, router, companySrv, attachSrv, contentSrv, permissionSrv, permissionSyncSrv)

		// 各模块初始化时已注册权限，先同步权限目录，再初始化管理员角色
		if err := permissionSyncSrv.Sync(stdContext.Background()); err != nil {
			log.Fatal(err)
		}
		if err := securityInitializer.Initialize(stdContext.Background()); err != nil {
			log.Fatal(err)
		}
	}

	if err := server.Listen(fmt.Sprintf(":%d", cfg.Server.Port)); err != nil {
		log.Fatal(err)
	}
}
