package company

import (
	"backend-go/internal/security"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

func Setup(ctx *context.AppContext, router fiber.Router, permissionSrv *security.Service,
	permissionSyncSrv *security.PermissionSyncService) *Service {
	permissionSyncSrv.Register(companyPermissions()...)
	companyRepo := NewCompanyRepository(ctx.Db)
	companyService := NewCompanyService(companyRepo)
	companyController := NewController(companyService)
	companyGroup := router.Group("/company")
	{
		companyGroup.Get("/", security.Middleware(permissionSrv, QueryAuthCode), companyController.GetAll)
		companyGroup.Post("/", security.Middleware(permissionSrv, CreateAuthCode), companyController.Create)
		companyGroup.Put("/", security.Middleware(permissionSrv, UpdateAuthCode), companyController.Update)
		companyGroup.Delete("/:id", security.Middleware(permissionSrv, DisableAuthCode), companyController.Disable)
	}
	return companyService
}

func SetupForTest(ctx *context.AppContext) *Service {
	companyRepo := NewCompanyRepository(ctx.Db)
	companyService := NewCompanyService(companyRepo)
	return companyService
}
