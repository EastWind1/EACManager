package servicebill

import (
	attachSrv "backend-go/internal/attach/service"
	"backend-go/internal/common/auth"
	"backend-go/internal/common/context"
	companySrv "backend-go/internal/company/service"
	"backend-go/internal/servicebill/controller"
	"backend-go/internal/servicebill/repository"
	"backend-go/internal/servicebill/service"

	"github.com/gofiber/fiber/v2"
)

func Setup(ctx *context.AppContext, router fiber.Router, companySrv *companySrv.CompanyService, attachSrv *attachSrv.AttachmentService, attachMapSrv *attachSrv.AttachMapService) {
	serviceBillRepo := repository.NewServiceBillRepository(ctx.Db)
	bizSrv := service.NewBizService(ctx.Cache, serviceBillRepo, attachSrv, attachMapSrv)
	wkMapRule := service.NewWKMapRule(companySrv)
	ldMapRule := service.NewLDMapRule(wkMapRule)
	attachMapSrv.RegisterRule(wkMapRule)
	attachMapSrv.RegisterRule(ldMapRule)
	serviceBillController := controller.NewServiceBillController(bizSrv)

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
		serviceBillGroup.Post("/export", serviceBillController.Export)
	}
	statisticSrv := service.NewStatisticService(ctx.Cache, serviceBillRepo)
	statisticController := controller.NewStatisticController(statisticSrv)
	statisticGroup := router.Group("/statistic", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser))
	{
		statisticGroup.Get("/billCountByState", statisticController.CountBillsByState)
		statisticGroup.Get("/billTotalAmountGroupByMonth", statisticController.SumAmountByMonth)
	}
}
