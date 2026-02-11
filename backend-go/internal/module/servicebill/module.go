package servicebill

import (
	service3 "backend-go/internal/module/attach/service"
	companySrv "backend-go/internal/module/company/service"
	controller2 "backend-go/internal/module/servicebill/controller"
	"backend-go/internal/module/servicebill/repository"
	service2 "backend-go/internal/module/servicebill/service"
	"backend-go/internal/pkg/auth"
	"backend-go/internal/pkg/context"

	"github.com/gofiber/fiber/v2"
)

func Setup(ctx *context.AppContext, router fiber.Router, companySrv *companySrv.CompanyService, attachSrv *service3.AttachmentService, attachMapSrv *service3.AttachMapService) {
	serviceBillRepo := repository.NewServiceBillRepository(ctx.Db)
	bizSrv := service2.NewBizService(ctx.Cache, serviceBillRepo, attachSrv, attachMapSrv)
	wkMapRule := service2.NewWKMapRule(companySrv)
	ldMapRule := service2.NewLDMapRule(wkMapRule)
	attachMapSrv.RegisterRule(wkMapRule)
	attachMapSrv.RegisterRule(ldMapRule)
	serviceBillController := controller2.NewServiceBillController(bizSrv)

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
	statisticSrv := service2.NewStatisticService(ctx.Cache, serviceBillRepo)
	statisticController := controller2.NewStatisticController(statisticSrv)
	statisticGroup := router.Group("/statistic", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser))
	{
		statisticGroup.Get("/billCountByState", statisticController.CountBillsByState)
		statisticGroup.Get("/billTotalAmountGroupByMonth", statisticController.SumAmountByMonth)
	}
}
