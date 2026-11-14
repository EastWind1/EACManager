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
	"backend-go/pkg/core"
	"backend-go/pkg/database"
	"backend-go/pkg/logger"
	"backend-go/pkg/middleware"
	"fmt"

	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/log"
	"github.com/gofiber/fiber/v3/middleware/recover"
)

// App 组装完成的应用程序；基础设施字段由 core.App 提供。
type App struct {
	*core.App

	Security  *security.Module
	User      *user.Service
	Company   *company.Service
	Attach    *attach.Module
	Bill      *bill.Module
	Reimburse *reimburse.Module
}

func Run() {
	app, err := NewApp(config.NewConfig())
	if err != nil {
		log.Fatal(err)
	}
	if err := app.Server.Listen(fmt.Sprintf(":%d", app.Config.Server.Port)); err != nil {
		log.Fatal(err)
	}
}

func NewApp(cfg *config.Config) (*App, error) {
	base := &core.App{
		Config: cfg,
		Cache:  cache.NewInMemoryCache(cfg.Cache),
		DB:     database.NewDB(cfg.Db, cfg.Log),
		Server: fiber.New(fiber.Config{
			ErrorHandler: middleware.ErrorHandler(),
			BodyLimit:    int(cfg.Attach.MaxFileSize),
		}),
	}
	base.Router = base.Server.Group("/api")

	app := &App{App: base}
	logger.InitLogger(cfg.Log)
	base.Server.Use(recover.New(recover.Config{
		EnableStackTrace: true,
	}))
	base.Server.Use(middleware.ResultWrap())

	app.Security = security.Setup(base)
	app.User = user.Setup(base, app.Security)
	app.Company = company.Setup(base, app.Security)
	app.Attach = attach.Setup(base, app.Security)
	app.Reimburse = reimburse.Setup(base, app.Attach, app.Security)
	app.Bill = bill.Setup(base, app.Company, app.Attach, app.User, app.Security)

	if err := app.Security.PermissionSync.Sync(); err != nil {
		return nil, err
	}
	return app, nil
}
