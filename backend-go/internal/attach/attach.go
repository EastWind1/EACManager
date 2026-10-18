package attach

import (
	"backend-go/pkg/auth"
	appContext "backend-go/pkg/context"
	"context"
	"time"

	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/log"
)

func Setup(ctx *appContext.AppContext, router fiber.Router) (*Service, *ContentService) {
	attachmentRepo := NewRepository(ctx.Db)
	attachmentService := NewService(ctx.Cfg.Attach, ctx.Cache, attachmentRepo)
	ocrService := NewOCRService(ctx.Cfg.OCR)
	attachmentController := NewController(attachmentService)
	attachGroup := router.Group("/attachment")
	{
		attachGroup.Get("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), attachmentController.Download)
		attachGroup.Post("/upload", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), attachmentController.Upload)
	}

	go func() {
		ticker := time.NewTicker(24 * time.Hour)
		defer ticker.Stop()
		for range ticker.C {
			if err := attachmentService.CleanTransientFiles(context.Background()); err != nil {
				log.Errorf("回收游离附件失败: %v", err)
			}
		}
	}()

	return attachmentService, NewContentService(ocrService, attachmentService)
}

func SetupForTest(ctx *appContext.AppContext) (*Service, *ContentService) {
	attachmentRepo := NewRepository(ctx.Db)
	attachmentService := NewService(ctx.Cfg.Attach, ctx.Cache, attachmentRepo)
	ocrService := NewOCRService(ctx.Cfg.OCR)
	return attachmentService, NewContentService(ocrService, attachmentService)
}
