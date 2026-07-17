package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/pkg/auth"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

func Setup(ctx *context.AppContext, router fiber.Router, attachSrc *attach.Service, attachMapSrc *attach.MapService) {
	reimburseRepo := NewReimburseRepository(ctx.Db)
	reimburseService := NewService(reimburseRepo, attachSrc, attachMapSrc, ctx.Cache)
	reimburseController := NewReimburseController(reimburseService)
	reimburseGroup := router.Group("/reimburse")
	{
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

	attachMapSrc.RegisterRule(NewMapRule())
}

func SetupForTest(ctx *context.AppContext, attachSrv *attach.Service, attachMapSrv *attach.MapService) *Service {
	reimburseRepo := NewReimburseRepository(ctx.Db)
	reimburseService := NewService(reimburseRepo, attachSrv, attachMapSrv, ctx.Cache)
	attachMapSrv.RegisterRule(NewMapRule())
	return reimburseService
}
