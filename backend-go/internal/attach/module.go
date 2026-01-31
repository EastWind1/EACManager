package attach

import (
	"backend-go/internal/attach/controller"
	"backend-go/internal/attach/hook"
	"backend-go/internal/attach/repository"
	"backend-go/internal/attach/service"
	"backend-go/internal/common/auth"
	"backend-go/internal/common/context"

	"github.com/gofiber/fiber/v2"
)

func Setup(ctx *context.AppContext, router fiber.Router) (*service.AttachmentService, *service.AttachMapService) {
	attachmentRepo := repository.NewAttachmentRepository(ctx.Db)
	billAttachRepo := repository.NewBillAttachRelationRepository(ctx.Db)
	attachmentService := service.NewAttachmentService(ctx.Cfg.Attachment, ctx.Cache, attachmentRepo, billAttachRepo)
	ocrService := service.NewOCRService(ctx.Cfg.OCR)
	attachMapService := service.NewAttachMapService(ctx.Cache, ocrService, attachmentService)
	attachmentController := controller.NewAttachmentController(attachmentService)
	attachGroup := router.Group("/attachment")
	{
		attachGroup.Get("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), attachmentController.Download)
		attachGroup.Post("/temp", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), attachmentController.UploadTemp)
	}

	hook.SetupCleanOnExit(ctx.Cache)

	return attachmentService, attachMapService
}
