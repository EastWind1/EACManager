package user

import (
	"backend-go/pkg/auth"
	"backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

// Setup 初始化
func Setup(ctx *context.AppContext, router fiber.Router) {
	repo := NewUserRepository(ctx.Db)
	jwtSrv := NewJWTService(ctx.Cfg.JWT)
	userSrv := NewService(repo, jwtSrv)
	userController := NewController(ctx.Cfg.JWT, userSrv)

	router.Use(AuthMiddleware(jwtSrv, userSrv))

	api := router.Group("/user")
	{
		api.Post(LoginPath, userController.Login)
		api.Get("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), userController.GetAll)
		api.Post("/", auth.RoleMiddleware(auth.RoleAdmin), userController.Create)
		api.Put("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), userController.Update)
		api.Delete("/:username", auth.RoleMiddleware(auth.RoleAdmin), userController.Disable)
	}
}

func SetupForTest(ctx *context.AppContext) *Service {
	repo := NewUserRepository(ctx.Db)
	jwtSrv := NewJWTService(ctx.Cfg.JWT)
	userSrv := NewService(repo, jwtSrv)
	return userSrv
}
