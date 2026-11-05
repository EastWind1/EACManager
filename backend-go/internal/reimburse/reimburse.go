package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/internal/security"
	"backend-go/pkg/cache"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
)

func Setup(cacheStore cache.Cache, db *gorm.DB, router fiber.Router, attachSrc *attach.Service, contentSrc *attach.ContentService,
	permissionSrv *security.Service, permissionSyncSrv *security.PermissionSyncService) (*Service, *StatisticService) {
	permissionSyncSrv.Register(reimbursePermissions()...)
	reimburseRepo := newReimburseRepository(db)
	reimburseMapper := newMapper(contentSrc)
	reimburseService := newService(reimburseRepo, attachSrc, newAttachRelService(db, attachSrc), reimburseMapper)
	statisticSrv := newStatisticService(cacheStore, reimburseRepo)
	reimburseController := newReimburseController(reimburseService)
	statisticController := newStatisticController(statisticSrv)
	reimburseGroup := router.Group("/reimburse")
	{
		reimburseGroup.Get("/countByState", security.Middleware(permissionSrv, StatisticAuthCode), statisticController.CountReimburseByState)
		reimburseGroup.Get("/totalAmountGroupByMonth", security.Middleware(permissionSrv, StatisticAuthCode), statisticController.SumAmountByMonth)
		reimburseGroup.Post("/query", security.Middleware(permissionSrv, QueryAuthCode), reimburseController.QueryByParam)
		reimburseGroup.Get("/:id", security.Middleware(permissionSrv, DetailAuthCode), reimburseController.GetByID)
		reimburseGroup.Post("/", security.Middleware(permissionSrv, CreateAuthCode), reimburseController.Create)
		reimburseGroup.Put("/", security.Middleware(permissionSrv, UpdateAuthCode), reimburseController.Update)
		reimburseGroup.Delete("/", security.Middleware(permissionSrv, DeleteAuthCode), reimburseController.Delete)
		reimburseGroup.Put("/process", security.Middleware(permissionSrv, ProcessAuthCode), reimburseController.Process)
		reimburseGroup.Put("/finish", security.Middleware(permissionSrv, FinishAuthCode), reimburseController.Finish)
		reimburseGroup.Put("/cancel-process", security.Middleware(permissionSrv, CancelProcessAuthCode), reimburseController.CancelProcess)
		reimburseGroup.Put("/cancel-finish", security.Middleware(permissionSrv, CancelFinishAuthCode), reimburseController.CancelFinish)
		reimburseGroup.Post("/export", security.Middleware(permissionSrv, ExportAuthCode), reimburseController.Export)
		reimburseGroup.Post("/import", security.Middleware(permissionSrv, ImportAuthCode), reimburseController.ImportByFile)
	}
	return reimburseService, statisticSrv
}
