package company

import (
	"backend-go/internal/common/auth"
	"backend-go/internal/common/context"
	"backend-go/internal/company/controller"
	"backend-go/internal/company/repository"
	"backend-go/internal/company/service"

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
