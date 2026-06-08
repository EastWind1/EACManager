package attach

import (
	"backend-go/pkg/auth"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

func Setup(ctx *context.AppContext, router fiber.Router) (*Service, *MapService) {
	attachmentRepo := NewRepository(ctx.Db)
	billAttachRepo := NewBillAttachRelRepo(ctx.Db)
	attachmentService := NewService(ctx.Cfg.Attachment, ctx.Cache, attachmentRepo, billAttachRepo)
	ocrService := NewOCRService(ctx.Cfg.OCR)
	attachMapService := NewMapService(ctx.Cache, ocrService, attachmentService)
	attachmentController := NewController(attachmentService)
	attachGroup := router.Group("/attachment")
	{
		attachGroup.Get("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), attachmentController.Download)
		attachGroup.Post("/temp", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), attachmentController.UploadTemp)
	}

	ctx.Server.Hooks().OnPostShutdown(func(err error) error {
		DeleteTempFiles(ctx.Cache)
		return nil
	})

	return attachmentService, attachMapService
}

func SetupForTest(ctx *context.AppContext) (*Service, *MapService) {
	attachmentRepo := NewRepository(ctx.Db)
	billAttachRepo := NewBillAttachRelRepo(ctx.Db)
	attachmentService := NewService(ctx.Cfg.Attachment, ctx.Cache, attachmentRepo, billAttachRepo)
	ocrService := NewOCRService(ctx.Cfg.OCR)
	attachMapService := NewMapService(ctx.Cache, ocrService, attachmentService)
	return attachmentService, attachMapService
}
