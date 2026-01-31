package reimburse

import (
	attachSrv "backend-go/internal/attach/service"
	"backend-go/internal/common/auth"
	"backend-go/internal/common/context"
	"backend-go/internal/reimburse/controller"
	"backend-go/internal/reimburse/repository"
	"backend-go/internal/reimburse/service"

	"github.com/gofiber/fiber/v2"
)

func Setup(ctx *context.AppContext, router fiber.Router, attachSrc *attachSrv.AttachmentService) {
	reimburseRepo := repository.NewReimburseRepository(ctx.Db)
	reimburseService := service.NewReimburseService(reimburseRepo, attachSrc, ctx.Cache)
	reimburseController := controller.NewReimburseController(reimburseService)
	companyGroup := router.Group("/reimburse")
	{
		companyGroup.Get("/query", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.QueryByParam)
		companyGroup.Get("/:id", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.GetByID)
		companyGroup.Post("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Create)
		companyGroup.Put("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Update)
		companyGroup.Delete("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Delete)
		companyGroup.Put("/process", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Process)
		companyGroup.Put("/finish", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), reimburseController.Finish)
		companyGroup.Post("/export", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), reimburseController.Export)
	}
}
