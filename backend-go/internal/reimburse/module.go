package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/internal/security"
	"backend-go/pkg/core"
)

// Module 聚合报销模块对外服务。
type Module struct {
	Service   *Service
	Statistic *StatisticService
}

func Setup(base *core.App, attachMod *attach.Module, sec *security.Module) *Module {
	sec.PermissionSync.Register(reimbursePermissions()...)
	reimburseRepo := newReimburseRepository(base.DB)
	reimburseMapper := newMapper(attachMod.Content)
	reimburseService := newService(reimburseRepo, attachMod.Service, newAttachRelService(base.DB, attachMod.Service), reimburseMapper)
	statisticSrv := newStatisticService(base.Cache, reimburseRepo)
	reimburseController := newReimburseController(reimburseService)
	statisticController := newStatisticController(statisticSrv)
	reimburseGroup := base.Router.Group("/reimburse")
	{
		reimburseGroup.Get("/countByState", sec.Middleware(StatisticAuthCode), statisticController.CountReimburseByState)
		reimburseGroup.Get("/totalAmountGroupByMonth", sec.Middleware(StatisticAuthCode), statisticController.SumAmountByMonth)
		reimburseGroup.Post("/query", sec.Middleware(QueryAuthCode), reimburseController.QueryByParam)
		reimburseGroup.Get("/:id", sec.Middleware(DetailAuthCode), reimburseController.GetByID)
		reimburseGroup.Post("/", sec.Middleware(CreateAuthCode), reimburseController.Create)
		reimburseGroup.Put("/", sec.Middleware(UpdateAuthCode), reimburseController.Update)
		reimburseGroup.Delete("/", sec.Middleware(DeleteAuthCode), reimburseController.Delete)
		reimburseGroup.Put("/process", sec.Middleware(ProcessAuthCode), reimburseController.Process)
		reimburseGroup.Put("/finish", sec.Middleware(FinishAuthCode), reimburseController.Finish)
		reimburseGroup.Put("/cancel-process", sec.Middleware(CancelProcessAuthCode), reimburseController.CancelProcess)
		reimburseGroup.Put("/cancel-finish", sec.Middleware(CancelFinishAuthCode), reimburseController.CancelFinish)
		reimburseGroup.Post("/export", sec.Middleware(ExportAuthCode), reimburseController.Export)
		reimburseGroup.Post("/import", sec.Middleware(ImportAuthCode), reimburseController.ImportByFile)
	}
	return &Module{
		Service:   reimburseService,
		Statistic: statisticSrv,
	}
}
