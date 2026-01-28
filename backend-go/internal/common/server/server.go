package server

import (
	"backend-go/config"
	"backend-go/internal/common/cache"
	"backend-go/internal/common/context"
	"backend-go/internal/common/database"
	"backend-go/internal/common/logger"
	"backend-go/internal/common/middleware"
	"backend-go/internal/company"
	"backend-go/internal/user"
	"fmt"

	"github.com/bytedance/sonic"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/log"
	"github.com/gofiber/fiber/v2/middleware/recover"
)

// Run 启动服务
func Run() {
	// 初始化上下文
	appContext := &context.AppContext{}
	// 加载配置
	cfg := config.NewConfig()
	appContext.Cfg = cfg
	// 初始化服务
	server := fiber.New(fiber.Config{
		JSONEncoder:  sonic.Marshal,
		JSONDecoder:  sonic.Unmarshal,
		ErrorHandler: middleware.ErrorHandler(),
	})
	appContext.Server = server
	// 初始化日志
	logger.InitLogger(cfg.Log)
	// 初始化异常处理
	server.Use(recover.New())
	// 初始化响应体包装
	server.Use(middleware.ResultWrap())
	// 初始化缓存
	appContext.Cache = cache.NewCache(cfg.Cache)
	// 初始化数据库
	appContext.Db = database.NewDB(cfg.Db)
	// 初始化路由
	router := server.Group("/api")
	// 初始化模块
	{
		user.Setup(appContext, router)
		company.Setup(appContext, router)
	}

	if err := server.Listen(fmt.Sprintf(":%d", cfg.Server.Port)); err != nil {
		log.Fatal(err)
	}
}
