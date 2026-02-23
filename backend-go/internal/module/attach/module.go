package attach

import (
	"backend-go/internal/module/attach/controller"
	"backend-go/internal/module/attach/hook"
	"backend-go/internal/module/attach/repository"
	"backend-go/internal/module/attach/service"
	"backend-go/internal/pkg/auth"
	"backend-go/internal/pkg/context"

	"github.com/gofiber/fiber/v3"
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

	ctx.Server.Hooks().OnPostShutdown(func(err error) error {
		hook.DeleteTempFiles(ctx.Cache)
		return nil
	})

	return attachmentService, attachMapService
}

func SetupForTest(ctx *context.AppContext) (*service.AttachmentService, *service.AttachMapService) {
	attachmentRepo := repository.NewAttachmentRepository(ctx.Db)
	billAttachRepo := repository.NewBillAttachRelationRepository(ctx.Db)
	attachmentService := service.NewAttachmentService(ctx.Cfg.Attachment, ctx.Cache, attachmentRepo, billAttachRepo)
	ocrService := service.NewOCRService(ctx.Cfg.OCR)
	attachMapService := service.NewAttachMapService(ctx.Cache, ocrService, attachmentService)
	return attachmentService, attachMapService
}
