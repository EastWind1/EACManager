package company

import (
	"backend-go/internal/security"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
)

func Setup(db *gorm.DB, router fiber.Router, permissionSrv *security.Service,
	permissionSyncSrv *security.PermissionSyncService) *Service {
	permissionSyncSrv.Register(companyPermissions()...)
	companyRepo := newCompanyRepository(db)
	companyService := newCompanyService(companyRepo)
	companyController := newController(companyService)
	companyGroup := router.Group("/company")
	{
		companyGroup.Get("/", security.Middleware(permissionSrv, QueryAuthCode), companyController.GetAll)
		companyGroup.Post("/", security.Middleware(permissionSrv, CreateAuthCode), companyController.Create)
		companyGroup.Put("/", security.Middleware(permissionSrv, UpdateAuthCode), companyController.Update)
		companyGroup.Delete("/:id", security.Middleware(permissionSrv, DisableAuthCode), companyController.Disable)
	}
	return companyService
}
