package bill

import (
	"backend-go/internal/attach"
	"backend-go/internal/company"
	"backend-go/internal/security"
	"backend-go/internal/user"
	"backend-go/pkg/core"
)

// Module 聚合服务单模块对外服务。
type Module struct {
	Service   *Service
	Query     *QueryService
	Statistic *StatisticService
}

func Setup(base *core.App, companySrv *company.Service, attachMod *attach.Module, userSrv *user.Service,
	sec *security.Module) *Module {
	sec.PermissionSync.Register(billPermissions()...)
	serviceBillRepo := newRepository(base.DB)
	relSrv := newAttachRelService(base.DB, attachMod.Service)
	querySrv := newQueryService(serviceBillRepo, relSrv, userSrv, sec.Authorize)
	srv := newService(base.Cache, serviceBillRepo, attachMod.Service, relSrv, newServiceBillMapper(attachMod.Content, companySrv), querySrv, userSrv)
	statisticSrv := newStatisticService(base.Cache, serviceBillRepo, querySrv)
	serviceBillController := newController(srv, querySrv)
	statisticController := newStatisticController(statisticSrv)

	serviceBillGroup := base.Router.Group("/serviceBill")
	{
		serviceBillGroup.Get("/countByState", sec.Middleware(StatisticAuthCode), statisticController.CountBillsByState)
		serviceBillGroup.Get("/totalAmountGroupByMonth", sec.Middleware(StatisticAuthCode), statisticController.SumAmountByMonth)

		serviceBillGroup.Post("/query", sec.Middleware(QueryAuthCode), serviceBillController.QueryByParam)
		serviceBillGroup.Get("/assignees", sec.Middleware(AssignAuthCode), serviceBillController.GetAssignees)
		serviceBillGroup.Put("/assign", sec.Middleware(AssignAuthCode), serviceBillController.Assign)
		serviceBillGroup.Get("/:id", sec.Middleware(DetailAuthCode), serviceBillController.GetByID)
		serviceBillGroup.Post("/", sec.Middleware(CreateAuthCode), serviceBillController.Create)
		serviceBillGroup.Post("/import", sec.Middleware(ImportAuthCode), serviceBillController.ImportByFile)
		serviceBillGroup.Put("/", sec.Middleware(UpdateAuthCode), serviceBillController.Update)
		serviceBillGroup.Delete("/", sec.Middleware(DeleteAuthCode), serviceBillController.Delete)
		serviceBillGroup.Put("/process", sec.Middleware(ProcessAuthCode), serviceBillController.Process)
		serviceBillGroup.Put("/processed", sec.Middleware(ProcessedAuthCode), serviceBillController.Processed)
		serviceBillGroup.Put("/finish", sec.Middleware(FinishAuthCode), serviceBillController.Finish)
		serviceBillGroup.Put("/cancel-process", sec.Middleware(CancelProcessAuthCode), serviceBillController.CancelProcess)
		serviceBillGroup.Put("/cancel-processed", sec.Middleware(CancelProcessedAuthCode), serviceBillController.CancelProcessed)
		serviceBillGroup.Put("/cancel-finish", sec.Middleware(CancelFinishAuthCode), serviceBillController.CancelFinish)
		serviceBillGroup.Post("/export", sec.Middleware(ExportAuthCode), serviceBillController.Export)
	}
	return &Module{
		Service:   srv,
		Query:     querySrv,
		Statistic: statisticSrv,
	}
}
