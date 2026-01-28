package attach

import (
	"backend-go/config"
	"backend-go/internal/attach/controller"
	"backend-go/internal/attach/repository"
	"backend-go/internal/attach/service"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

var (
	attachmentRepo       *repository.AttachmentRepository
	billAttachRepo       *repository.BillAttachRelationRepository
	attachmentService    *service.AttachmentService
	attachmentController *controller.AttachmentController
)

func Setup(cfg *config.AttachmentConfig, db *gorm.DB, router *gin.RouterGroup) {
	attachmentRepo = repository.NewAttachmentRepository(db)
	billAttachRepo = repository.NewBillAttachRelationRepository(db)
	attachmentService = service.NewAttachmentService(attachmentRepo, billAttachRepo, cfg)
	attachmentController = controller.NewAttachmentController(attachmentService)
	attachGroup := router.Group("/attachment")
	{
		attachGroup.GET("/*path", attachmentController.GetFile)
		attachGroup.POST("/temp", attachmentController.UploadTemp)

	}
}
