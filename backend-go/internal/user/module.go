package user

import (
	"backend-go/internal/common/auth"
	"backend-go/internal/common/context"
	"backend-go/internal/user/controller"
	"backend-go/internal/user/middleware"
	"backend-go/internal/user/repository"
	"backend-go/internal/user/service"

	"github.com/gofiber/fiber/v2"
)

// Setup 初始化
func Setup(ctx *context.AppContext, router fiber.Router) {
	repo := repository.NewUserRepository(ctx.Db)
	jwtSrv := service.NewJWTService(ctx.Cfg.JWT)
	userSrv := service.NewUserService(repo, jwtSrv)
	userController := controller.NewUserController(ctx.Cfg.JWT, userSrv)

	router.Use(middleware.AuthMiddleware(jwtSrv, userSrv))

	api := router.Group("/user")
	{
		api.Post(middleware.LoginPath, userController.Login)
		api.Get("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), userController.GetAll)
		api.Post("/", auth.RoleMiddleware(auth.RoleAdmin), userController.Create)
		api.Put("/", auth.RoleMiddleware(auth.RoleAdmin, auth.RoleUser, auth.RoleFinance), userController.Update)
		api.Delete("/:username", auth.RoleMiddleware(auth.RoleAdmin), userController.Disable)
	}
}
