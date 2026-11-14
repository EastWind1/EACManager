package company

import (
	"backend-go/internal/security"
	"backend-go/pkg/core"
)

func Setup(base *core.App, sec *security.Module) *Service {
	sec.PermissionSync.Register(companyPermissions()...)
	companyRepo := newCompanyRepository(base.DB)
	companyService := newCompanyService(companyRepo)
	companyController := newController(companyService)
	companyGroup := base.Router.Group("/company")
	{
		companyGroup.Get("/", sec.Middleware(QueryAuthCode), companyController.GetAll)
		companyGroup.Post("/", sec.Middleware(CreateAuthCode), companyController.Create)
		companyGroup.Put("/", sec.Middleware(UpdateAuthCode), companyController.Update)
		companyGroup.Delete("/:id", sec.Middleware(DisableAuthCode), companyController.Disable)
	}
	return companyService
}
