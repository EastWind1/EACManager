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

func Run() {
	ctx := new(context.AppContext)
	cfg := config.NewConfig()
	ctx.Cfg = cfg
	server := fiber.New(fiber.Config{
		ErrorHandler: middleware.ErrorHandler(),
		BodyLimit:    int(cfg.Attach.MaxFileSize),
	})
	ctx.Server = server
	logger.InitLogger(cfg.Log)
	server.Use(cors.New())
	server.Use(recover.New(recover.Config{
		EnableStackTrace: true,
	}))
	server.Use(middleware.ResultWrap())
	ctx.Cache = cache.NewInMemoryCache(cfg.Cache)
	ctx.Db = database.NewDB(cfg.Db, cfg.Log)
	router := server.Group("/api")
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
