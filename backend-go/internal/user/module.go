package user

import (
	"backend-go/internal/security"
	"backend-go/pkg/core"
)

func Setup(base *core.App, sec *security.Module) *Service {
	sec.PermissionSync.Register(userPermissions()...)
	repo := newUserRepository(base.DB)
	userSrv := newService(repo, sec)
	userController := newController(base.Config.JWT, userSrv)

	api := base.Router.Group("/user")
	{
		api.Post("/token", userController.Login)
		api.Get("/initialization", userController.InitializationStatus)
		api.Post("/initialization", userController.Initialize)
		api.Put("/logout", userController.Logout)
		api.Get("/", sec.Middleware(QueryAuthCode), userController.GetAll)
		api.Post("/", sec.Middleware(CreateAuthCode), userController.Create)
		api.Put("/", sec.Middleware(UpdateAuthCode), userController.Update)
	}
	return userSrv
}
