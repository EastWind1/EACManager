package server

import (
	"backend-go/config"
	"backend-go/internal/module/attach"
	"backend-go/internal/module/company"
	"backend-go/internal/module/reimburse"
	"backend-go/internal/module/servicebill"
	"backend-go/internal/module/user"
	"backend-go/internal/pkg/cache"
	"backend-go/internal/pkg/context"
	"backend-go/internal/pkg/database"
	"backend-go/internal/pkg/logger"
	"backend-go/internal/pkg/middleware"
	"fmt"

	"github.com/bytedance/sonic"
	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/log"
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
		JSONEncoder:  sonic.Marshal,
		JSONDecoder:  sonic.Unmarshal,
		ErrorHandler: middleware.ErrorHandler(),
	})
	ctx.Server = server
	// 初始化日志
	logger.InitLogger(cfg.Log)
	// 初始化异常处理
	server.Use(recover.New())
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
		user.Setup(ctx, router)
		companySrv := company.Setup(ctx, router)
		attachSrv, attachMapSrv := attach.Setup(ctx, router)
		reimburse.Setup(ctx, router, attachSrv)
		servicebill.Setup(ctx, router, companySrv, attachSrv, attachMapSrv)
	}

	if err := server.Listen(fmt.Sprintf(":%d", cfg.Server.Port)); err != nil {
		log.Fatal(err)
	}
}
