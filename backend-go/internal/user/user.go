package user

import (
	"backend-go/internal/security"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

// Setup 初始化
func Setup(ctx *context.AppContext, router fiber.Router, securitySrv *security.Service,
	permissionSyncSrv *security.PermissionSyncService) {
	permissionSyncSrv.Register(userPermissions()...)
	repo := NewUserRepository(ctx.Db)
	jwtSrv := NewJWTService(ctx.Cfg.JWT)
	userSrv := NewService(repo, jwtSrv, securitySrv)
	userController := NewController(ctx.Cfg.JWT, userSrv)

	router.Use(AuthMiddleware(jwtSrv, userSrv))

	api := router.Group("/user")
	{
		api.Post(LoginPath, userController.Login)
		api.Get("/initialization", userController.InitializationStatus)
		api.Post("/initialization", userController.Initialize)
		api.Put("/logout", userController.Logout)
		api.Get("/", security.Middleware(securitySrv, QueryAuthCode), userController.GetAll)
		api.Post("/", security.Middleware(securitySrv, CreateAuthCode), userController.Create)
		api.Put("/", security.Middleware(securitySrv, UpdateAuthCode), userController.Update)
		api.Delete("/:username", security.Middleware(securitySrv, DisableAuthCode), userController.Disable)
	}
}

func SetupForTest(ctx *context.AppContext) *Service {
	repo := NewUserRepository(ctx.Db)
	jwtSrv := NewJWTService(ctx.Cfg.JWT)
	securitySrv := security.NewService(ctx)
	return NewService(repo, jwtSrv, securitySrv)
}
