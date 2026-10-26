package bill

import (
	"backend-go/internal/attach"
	"backend-go/internal/company"
	"backend-go/internal/security"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

func Setup(ctx *context.AppContext, router fiber.Router, companySrv *company.Service, attachSrv *attach.Service,
	contentSrv *attach.ContentService, permissionSrv *security.Service,
	permissionSyncSrv *security.PermissionSyncService) {
	permissionSyncSrv.Register(billPermissions()...)
	serviceBillRepo := NewRepository(ctx.Db)
	relSrv := NewAttachRelService(ctx.Db, attachSrv)
	srv := NewService(ctx.Cache, serviceBillRepo, attachSrv, relSrv, NewServiceBillMapper(contentSrv, companySrv))
	serviceBillController := NewController(srv)
	statisticController := NewStatisticController(NewStatisticService(ctx.Cache, serviceBillRepo))

	serviceBillGroup := router.Group("/serviceBill")
	{
		serviceBillGroup.Get("/countByState", security.Middleware(permissionSrv, StatisticAuthCode), statisticController.CountBillsByState)
		serviceBillGroup.Get("/totalAmountGroupByMonth", security.Middleware(permissionSrv, StatisticAuthCode), statisticController.SumAmountByMonth)

		serviceBillGroup.Post("/query", security.Middleware(permissionSrv, QueryAuthCode), serviceBillController.QueryByParam)
		serviceBillGroup.Get("/:id", security.Middleware(permissionSrv, DetailAuthCode), serviceBillController.GetByID)
		serviceBillGroup.Post("/", security.Middleware(permissionSrv, CreateAuthCode), serviceBillController.Create)
		serviceBillGroup.Post("/import", security.Middleware(permissionSrv, ImportAuthCode), serviceBillController.ImportByFile)
		serviceBillGroup.Put("/", security.Middleware(permissionSrv, UpdateAuthCode), serviceBillController.Update)
		serviceBillGroup.Delete("/", security.Middleware(permissionSrv, DeleteAuthCode), serviceBillController.Delete)
		serviceBillGroup.Put("/process", security.Middleware(permissionSrv, ProcessAuthCode), serviceBillController.Process)
		serviceBillGroup.Put("/processed", security.Middleware(permissionSrv, ProcessedAuthCode), serviceBillController.Processed)
		serviceBillGroup.Put("/finish", security.Middleware(permissionSrv, FinishAuthCode), serviceBillController.Finish)
		serviceBillGroup.Put("/cancel-process", security.Middleware(permissionSrv, CancelProcessAuthCode), serviceBillController.CancelProcess)
		serviceBillGroup.Put("/cancel-processed", security.Middleware(permissionSrv, CancelProcessedAuthCode), serviceBillController.CancelProcessed)
		serviceBillGroup.Put("/cancel-finish", security.Middleware(permissionSrv, CancelFinishAuthCode), serviceBillController.CancelFinish)
		serviceBillGroup.Post("/export", security.Middleware(permissionSrv, ExportAuthCode), serviceBillController.Export)
	}
}

func SetupForTest(ctx *context.AppContext, attachSrv *attach.Service) (*Service, *StatisticService) {
	serviceBillRepo := NewRepository(ctx.Db)
	relSrv := NewAttachRelService(ctx.Db, attachSrv)
	srv := NewService(ctx.Cache, serviceBillRepo, attachSrv, relSrv, nil)
	statisticSrv := NewStatisticService(ctx.Cache, serviceBillRepo)
	return srv, statisticSrv
}
