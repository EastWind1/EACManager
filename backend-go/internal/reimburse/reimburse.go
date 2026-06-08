package reimburse

import (
	"backend-go/internal/attach"
	"backend-go/pkg/auth"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

func Setup(ctx *context.AppContext, router fiber.Router, attachSrc *attach.Service) {
	reimburseRepo := NewReimburseRepository(ctx.Db)
	reimburseService := NewService(reimburseRepo, attachSrc, ctx.Cache)
	reimburseController := NewReimburseController(reimburseService)
	companyGroup := router.Group("/reimburse")
	{
		companyGroup.Post("/query", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.QueryByParam)
		companyGroup.Get("/:id", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.GetByID)
		companyGroup.Post("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Create)
		companyGroup.Put("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Update)
		companyGroup.Delete("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Delete)
		companyGroup.Put("/process", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Process)
		companyGroup.Put("/finish", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Finish)
		companyGroup.Put("/cancel-process", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.CancelProcess)
		companyGroup.Put("/cancel-finish", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.CancelFinish)
		companyGroup.Post("/export", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.Export)
	}
}

func SetupForTest(ctx *context.AppContext, attachSrv *attach.Service) *Service {
	reimburseRepo := NewReimburseRepository(ctx.Db)
	reimburseService := NewService(reimburseRepo, attachSrv, ctx.Cache)
	return reimburseService
}
