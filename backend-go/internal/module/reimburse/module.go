package reimburse

import (
	attachSrv "backend-go/internal/module/attach/service"
	"backend-go/internal/module/reimburse/controller"
	"backend-go/internal/module/reimburse/repository"
	"backend-go/internal/module/reimburse/service"
	"backend-go/internal/pkg/auth"
	"backend-go/internal/pkg/context"

	"github.com/gofiber/fiber/v2"
)

func Setup(ctx *context.AppContext, router fiber.Router, attachSrc *attachSrv.AttachmentService) {
	reimburseRepo := repository.NewReimburseRepository(ctx.Db)
	reimburseService := service.NewReimburseService(reimburseRepo, attachSrc, ctx.Cache)
	reimburseController := controller.NewReimburseController(reimburseService)
	companyGroup := router.Group("/reimburse")
	{
		companyGroup.Post("/query", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.QueryByParam)
		companyGroup.Get("/:id", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.GetByID)
		companyGroup.Post("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Create)
		companyGroup.Put("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Update)
		companyGroup.Delete("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Delete)
		companyGroup.Put("/process", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Process)
		companyGroup.Put("/finish", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Finish)
		companyGroup.Post("/export", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.Export)
	}
}
