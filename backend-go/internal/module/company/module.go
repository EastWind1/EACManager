package company

import (
	"backend-go/internal/module/company/controller"
	"backend-go/internal/module/company/repository"
	"backend-go/internal/module/company/service"
	"backend-go/internal/pkg/auth"
	"backend-go/internal/pkg/context"

	"github.com/gofiber/fiber/v2"
)

// Setup 初始化
func Setup(ctx *context.AppContext, router fiber.Router) *service.CompanyService {
	companyRepo := repository.NewCompanyRepository(ctx.Db)
	companyService := service.NewCompanyService(companyRepo)
	companyController := controller.NewCompanyController(companyService)
	companyGroup := router.Group("/company")
	{
		companyGroup.Get("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), companyController.GetAll)
		companyGroup.Post("/", auth.RoleMiddleware(auth.RoleAdmin), companyController.Create)
		companyGroup.Put("/", companyController.Update)
		companyGroup.Delete("/:id", companyController.Disable)
	}
	return companyService
}
