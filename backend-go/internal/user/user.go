package user

import (
	"backend-go/config"
	"backend-go/internal/security"

	"github.com/gofiber/fiber/v3"
	"gorm.io/gorm"
)

func Setup(cfg *config.Config, db *gorm.DB, router fiber.Router, securitySrv *security.Service,
	permissionSyncSrv *security.PermissionSyncService) *Service {
	permissionSyncSrv.Register(userPermissions()...)
	repo := newUserRepository(db)
	jwtSrv := newJWTService(cfg.JWT)
	userSrv := newService(repo, jwtSrv, securitySrv)
	userController := newController(cfg.JWT, userSrv)

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
	return userSrv
}
