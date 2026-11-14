package security

import (
	"backend-go/pkg/core"

	"github.com/gofiber/fiber/v3"
)

type Module struct {
	// 权限同步服务
	PermissionSync *PermissionSyncService
	// 鉴权中间件
	AuthMid *Authorize
	// 认证服务
	Authenticate *AuthenticateService
	// 鉴权服务
	Authorize *AuthorizeService
}

// Middleware 返回单权限校验中间件。
func (s *Module) Middleware(permission string) fiber.Handler {
	return s.AuthMid.HasAuthority(permission)
}

// HasAnyAuthority 返回任一权限满足即可的中间件。
func (s *Module) HasAnyAuthority(permissions ...string) fiber.Handler {
	return s.AuthMid.HasAnyAuthority(permissions...)
}

// Setup 组装安全模块，并把 token 解析中间件注册在所有业务路由之前。
func Setup(base *core.App) *Module {
	authRepo := newAuthRepository(base.DB)
	srv := newAuthorizeService(authRepo)
	permissionSyncSrv := newPermissionSyncService(authRepo)
	permissionSyncSrv.Register(ModulePermissions()...)
	authenticateSrv := newAuthenticateService(newJWTService(base.Config.JWT), newAccountRepository(base.DB), srv)

	base.Router.Use(newAuthenticate(authenticateSrv.jwtSrv, authenticateSrv).Handler)

	controller := newController(srv)
	authorize := newAuthorize(srv)
	accountController := newAccountController(authenticateSrv)
	api := base.Router.Group("/security")
	{
		api.Get("/permissions", authorize.HasAuthority(QueryAuthCode), controller.Permissions)
		api.Get("/roles", authorize.HasAnyAuthority("user:query", QueryAuthCode), controller.Roles)
		api.Post("/role", authorize.HasAuthority(UpdateAuthCode), controller.CreateRole)
		api.Put("/role", authorize.HasAuthority(UpdateAuthCode), controller.UpdateRole)
		api.Delete("/role/:id", authorize.HasAuthority(UpdateAuthCode), controller.DeleteRole)
		api.Get("/role/:id/permissions", authorize.HasAuthority(QueryAuthCode), controller.RolePermissions)
		api.Put("/role/:id/permissions", authorize.HasAuthority(UpdateAuthCode), controller.UpdateRolePermissions)
	}
	base.Router.Delete("/user/:username", authorize.HasAuthority("user:disable"), accountController.Disable)
	return &Module{
		PermissionSync: permissionSyncSrv,
		AuthMid:        authorize,
		Authenticate:   authenticateSrv,
		Authorize:      srv,
	}
}
