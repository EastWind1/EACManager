package attach

import (
	"backend-go/internal/attach/controller"
	"backend-go/internal/attach/hook"
	"backend-go/internal/attach/repository"
	"backend-go/internal/attach/service"
	"backend-go/internal/common/context"

	"github.com/gin-gonic/gin"
)

var (
	attachmentRepo       *repository.AttachmentRepository
	billAttachRepo       *repository.BillAttachRelationRepository
	attachmentService    *service.AttachmentService
	attachmentController *controller.AttachmentController
)

func Setup(ctx *context.AppContext, router *gin.RouterGroup) {
	attachmentRepo = repository.NewAttachmentRepository(ctx.Db)
	billAttachRepo = repository.NewBillAttachRelationRepository(ctx.Db)
	attachmentService = service.NewAttachmentService(ctx.Cfg.Attachment, ctx.Cache, attachmentRepo, billAttachRepo)
	attachmentController = controller.NewAttachmentController(attachmentService)
	attachGroup := router.Group("/attachment")
	{
		attachGroup.GET("/", attachmentController.DownloadFile)
		attachGroup.POST("/temp", attachmentController.UploadTemp)
	}
	ctx.Server.Hooks().OnShutdown(func() error {
		hook.DeleteTempFiles(ctx.Cache)
		return nil
	})
}
