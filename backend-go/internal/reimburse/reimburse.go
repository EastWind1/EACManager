package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/pkg/auth"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

func Setup(ctx *context.AppContext, router fiber.Router, attachSrc *attach.Service, contentSrc *attach.ContentService) {
	reimburseRepo := NewReimburseRepository(ctx.Db)
	reimburseMapper := NewMapper(contentSrc)
	reimburseService := NewService(reimburseRepo, attachSrc, reimburseMapper)
	reimburseController := NewReimburseController(reimburseService)
	statisticController := NewStatisticController(NewStatisticService(ctx.Cache, reimburseRepo))
	reimburseGroup := router.Group("/reimburse")
	{
		reimburseGroup.Get("/countByState", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), statisticController.CountReimburseByState)
		reimburseGroup.Get("/totalAmountGroupByMonth", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), statisticController.SumAmountByMonth)
		reimburseGroup.Post("/query", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.QueryByParam)
		reimburseGroup.Get("/:id", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.GetByID)
		reimburseGroup.Post("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Create)
		reimburseGroup.Put("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Update)
		reimburseGroup.Delete("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Delete)
		reimburseGroup.Put("/process", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Process)
		reimburseGroup.Put("/finish", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Finish)
		reimburseGroup.Put("/cancel-process", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.CancelProcess)
		reimburseGroup.Put("/cancel-finish", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.CancelFinish)
		reimburseGroup.Post("/export", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.Export)
		reimburseGroup.Post("/import", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.ImportByFile)
	}
}

func SetupForTest(ctx *context.AppContext, attachSrv *attach.Service, contentSrv *attach.ContentService) *Service {
	reimburseRepo := NewReimburseRepository(ctx.Db)
	reimburseMapper := NewMapper(contentSrv)
	return NewService(reimburseRepo, attachSrv, reimburseMapper)
}
