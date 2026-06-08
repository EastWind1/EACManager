package company

import (
	"backend-go/pkg/auth"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

// Setup 初始化
func Setup(ctx *context.AppContext, router fiber.Router) *Service {
	companyRepo := NewCompanyRepository(ctx.Db)
	companyService := NewCompanyService(companyRepo)
	companyController := NewController(companyService)
	companyGroup := router.Group("/company")
	{
		companyGroup.Get("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser), companyController.GetAll)
		companyGroup.Post("/", auth.RoleMiddleware(auth.RoleAdmin), companyController.Create)
		companyGroup.Put("/", companyController.Update)
		companyGroup.Delete("/:id", companyController.Disable)
	}
	return companyService
}

func SetupForTest(ctx *context.AppContext) *Service {
	companyRepo := NewCompanyRepository(ctx.Db)
	companyService := NewCompanyService(companyRepo)
	return companyService
}
