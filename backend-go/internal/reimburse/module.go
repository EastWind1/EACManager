package reimburse

import (
	"backend-go/internal/reimburse/controller"
	"backend-go/internal/reimburse/repository"
	"backend-go/internal/reimburse/service"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

var (
	reimburseRepo       *repository.ReimburseRepository
	reimburseService    *service.ReimburseService
	reimburseController *controller.ReimburseController
)

func Setup(db *gorm.DB, router *gin.RouterGroup) {
	reimburseRepo = repository.NewReimburseRepository(db)
	reimburseService = service.NewReimburseService(reimburseRepo)
	reimburseController = controller.NewReimburseController(reimburseService)
	companyGroup := router.Group("/reimburse")
	{
		companyGroup.GET("", reimburseController.Query)

	}
}
