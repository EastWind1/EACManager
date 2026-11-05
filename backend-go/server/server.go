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
	"backend-go/pkg/database"
	"backend-go/pkg/logger"
	"backend-go/pkg/middleware"
	"fmt"

	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/log"
	"github.com/gofiber/fiber/v3/middleware/recover"
	"gorm.io/gorm"
)

// App 组装完成的应用程序；基础设施与各模块 service 都由它持有
type App struct {
	Cfg    *config.Config
	Cache  cache.Cache
	Db     *gorm.DB
	Server *fiber.App

	User          *user.Service
	Company       *company.Service
	Attach        *attach.Service
	Content       *attach.ContentService
	Bill          *bill.Service
	BillStat      *bill.StatisticService
	Reimburse     *reimburse.Service
	ReimburseStat *reimburse.StatisticService
	Security      *security.Service
}

func Run() {
	app, err := NewApp(config.NewConfig())
	if err != nil {
		log.Fatal(err)
	}
	if err := app.Server.Listen(fmt.Sprintf(":%d", app.Cfg.Server.Port)); err != nil {
		log.Fatal(err)
	}
}

func NewApp(cfg *config.Config) (*App, error) {
	app := &App{
		Cfg:   cfg,
		Cache: cache.NewInMemoryCache(cfg.Cache),
		Db:    database.NewDB(cfg.Db, cfg.Log),
		Server: fiber.New(fiber.Config{
			ErrorHandler: middleware.ErrorHandler(),
			BodyLimit:    int(cfg.Attach.MaxFileSize),
		}),
	}
	logger.InitLogger(cfg.Log)
	app.Server.Use(recover.New(recover.Config{
		EnableStackTrace: true,
	}))
	app.Server.Use(middleware.ResultWrap())

	router := app.Server.Group("/api")
	{
		securitySrv, permissionSyncSrv := security.Setup(app.Db, router)

		app.Security = securitySrv
		app.User = user.Setup(cfg, app.Db, router, securitySrv, permissionSyncSrv)
		app.Company = company.Setup(app.Db, router, securitySrv, permissionSyncSrv)
		app.Attach, app.Content = attach.Setup(cfg, app.Db, router, securitySrv, permissionSyncSrv)
		app.Reimburse, app.ReimburseStat = reimburse.Setup(app.Cache, app.Db, router, app.Attach, app.Content, securitySrv, permissionSyncSrv)
		app.Bill, app.BillStat = bill.Setup(app.Cache, app.Db, router, app.Company, app.Attach, app.Content, securitySrv, permissionSyncSrv)

		if err := permissionSyncSrv.Sync(); err != nil {
			return nil, err
		}
		return app, nil
	}
}
