package attach

import (
	"backend-go/internal/module/attach/controller"
	"backend-go/internal/module/attach/hook"
	"backend-go/internal/module/attach/repository"
	service2 "backend-go/internal/module/attach/service"
	"backend-go/internal/pkg/auth"
	"backend-go/internal/pkg/context"

	"github.com/gofiber/fiber/v2"
)

func Setup(ctx *context.AppContext, router fiber.Router) (*service2.AttachmentService, *service2.AttachMapService) {
	attachmentRepo := repository.NewAttachmentRepository(ctx.Db)
	billAttachRepo := repository.NewBillAttachRelationRepository(ctx.Db)
	attachmentService := service2.NewAttachmentService(ctx.Cfg.Attachment, ctx.Cache, attachmentRepo, billAttachRepo)
	ocrService := service2.NewOCRService(ctx.Cfg.OCR)
	attachMapService := service2.NewAttachMapService(ctx.Cache, ocrService, attachmentService)
	attachmentController := controller.NewAttachmentController(attachmentService)
	attachGroup := router.Group("/attachment")
	{
		attachGroup.Get("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), attachmentController.Download)
		attachGroup.Post("/temp", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), attachmentController.UploadTemp)
	}

	ctx.Server.Hooks().OnShutdown(func() error {
		hook.DeleteTempFiles(ctx.Cache)
		return nil
	})

	return attachmentService, attachMapService
}
