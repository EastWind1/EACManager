package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/internal/security"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

func Setup(ctx *context.AppContext, router fiber.Router, attachSrc *attach.Service, contentSrc *attach.ContentService,
	permissionSrv *security.Service, permissionSyncSrv *security.PermissionSyncService) {
	permissionSyncSrv.Register(reimbursePermissions()...)
	reimburseRepo := NewReimburseRepository(ctx.Db)
	reimburseMapper := NewMapper(contentSrc)
	reimburseService := NewService(reimburseRepo, attachSrc, NewAttachRelService(ctx.Db, attachSrc), reimburseMapper)
	reimburseController := NewReimburseController(reimburseService)
	statisticController := NewStatisticController(NewStatisticService(ctx.Cache, reimburseRepo))
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
}

func SetupForTest(ctx *context.AppContext, attachSrv *attach.Service, contentSrv *attach.ContentService) *Service {
	reimburseRepo := NewReimburseRepository(ctx.Db)
	reimburseMapper := NewMapper(contentSrv)
	return NewService(reimburseRepo, attachSrv, NewAttachRelService(ctx.Db, attachSrv), reimburseMapper)
}
