package security

import (
	appContext "backend-go/pkg/context"

	"github.com/gofiber/fiber/v3"
)

// Setup 注册角色与权限管理路由，并声明本模块的权限类型
func Setup(ctx *appContext.AppContext, router fiber.Router, srv *Service,
	permissionSyncSrv *PermissionSyncService) {
	permissionSyncSrv.Register(modulePermissions()...)
	controller := NewController(srv)
	api := router.Group("/security")
	{
		api.Get("/permissions", Middleware(srv, QueryAuthCode), controller.Permissions)
		api.Get("/roles", MiddlewareAny(srv, "user:query", QueryAuthCode), controller.Roles)
		api.Post("/role", Middleware(srv, UpdateAuthCode), controller.CreateRole)
		api.Put("/role", Middleware(srv, UpdateAuthCode), controller.UpdateRole)
		api.Delete("/role/:id", Middleware(srv, UpdateAuthCode), controller.DeleteRole)
		api.Get("/role/:id/permissions", Middleware(srv, QueryAuthCode), controller.RolePermissions)
		api.Put("/role/:id/permissions", Middleware(srv, UpdateAuthCode), controller.UpdateRolePermissions)
	}
}
