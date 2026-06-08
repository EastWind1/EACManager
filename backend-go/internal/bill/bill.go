package bill

import (
	"backend-go/internal/attach"
	"backend-go/internal/company"
	"backend-go/pkg/auth"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

func Setup(ctx *context.AppContext, router fiber.Router, companySrv *company.Service, attachSrv *attach.Service, attachMapSrv *attach.MapService) {
	serviceBillRepo := NewRepository(ctx.Db)
	bizSrv := NewBizService(ctx.Cache, serviceBillRepo, attachSrv, attachMapSrv)
	wkMapRule := NewWKMapRule(companySrv)
	ldMapRule := NewLDMapRule(wkMapRule)
	attachMapSrv.RegisterRule(wkMapRule)
	attachMapSrv.RegisterRule(ldMapRule)
	serviceBillController := NewController(bizSrv)

	serviceBillGroup := router.Group("/serviceBill", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser))
	{
		serviceBillGroup.Post("/query", serviceBillController.QueryByParam)
		serviceBillGroup.Get("/:id", serviceBillController.GetByID)
		serviceBillGroup.Post("/", serviceBillController.Create)
		serviceBillGroup.Post("/import", serviceBillController.ImportByFile)
		serviceBillGroup.Put("/", serviceBillController.Update)
		serviceBillGroup.Delete("/", serviceBillController.Delete)
		serviceBillGroup.Put("/process", serviceBillController.Process)
		serviceBillGroup.Put("/processed", serviceBillController.Processed)
		serviceBillGroup.Put("/finish", serviceBillController.Finish)
		serviceBillGroup.Put("/cancel-process", auth.RoleMiddleware(auth.RoleAdmin), serviceBillController.CancelProcess)
		serviceBillGroup.Put("/cancel-processed", auth.RoleMiddleware(auth.RoleAdmin), serviceBillController.CancelProcessed)
		serviceBillGroup.Put("/cancel-finish", auth.RoleMiddleware(auth.RoleAdmin), serviceBillController.CancelFinish)
		serviceBillGroup.Post("/export", serviceBillController.Export)
	}
	statisticSrv := NewStatisticService(ctx.Cache, serviceBillRepo)
	statisticController := NewStatisticController(statisticSrv)
	statisticGroup := router.Group("/statistic", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser))
	{
		statisticGroup.Get("/billCountByState", statisticController.CountBillsByState)
		statisticGroup.Get("/billTotalAmountGroupByMonth", statisticController.SumAmountByMonth)
	}
}

func SetupForTest(ctx *context.AppContext, attachSrv *attach.Service) (*BizService, *StatisticService) {
	serviceBillRepo := NewRepository(ctx.Db)
	bizSrv := NewBizService(ctx.Cache, serviceBillRepo, attachSrv, nil)
	statisticSrv := NewStatisticService(ctx.Cache, serviceBillRepo)
	return bizSrv, statisticSrv
}
