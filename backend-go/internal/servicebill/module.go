package servicebill

import (
	attachService "backend-go/internal/attach/service"
	companyRepository "backend-go/internal/company/repository"
	"backend-go/internal/servicebill/controller"
	"backend-go/internal/servicebill/repository"
	"backend-go/internal/servicebill/service"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

var (
	servicebillRepo       *repository.ServiceBillRepository
	servicebillService    *service.ServiceBillService
	servicebillController *controller.ServiceBillController
	attachSrvc            *attachService.AttachmentService
	companyRepo           *companyRepository.CompanyRepository
)

func Setup(db *gorm.DB, router *gin.RouterGroup) {
	// servicebillRepo = repository.NewServiceBillRepository(db)
	// servicebillService = service.NewServiceBillService(reimburseRepo)
	// servicebillController = controller.NewReimburseController(reimburseService)
	// companyGroup := router.Group("/attachment")
	// {
	// 	companyGroup.GET("", reimburseController.Query)

	// }
}
