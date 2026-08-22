package attach

import (
	"backend-go/pkg/auth"
	"backend-go/pkg/context"
	"time"

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

	go func() {
		ticker := time.NewTicker(24 * time.Hour)
		defer ticker.Stop()
		for range ticker.C {
			go attachmentService.CleanTempFiles()
		}
	}()

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
