package security

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"strings"

	"github.com/gofiber/fiber/v3"
)

// Authenticate 认证中间件，负责解析 TOKEN 并设置用户上下文
type Authenticate struct {
	jwtSrv          *JWTService
	authenticateSrv *AuthenticateService
}

func newAuthenticate(jwtSrv *JWTService, authenticateSrv *AuthenticateService) *Authenticate {
	return &Authenticate{jwtSrv: jwtSrv, authenticateSrv: authenticateSrv}
}

func (m *Authenticate) Handler(c fiber.Ctx) error {
	// 公开接口
	if c.Path() == "/api/user/token" && c.Method() == "POST" {
		return c.Next()
	}
	if c.Path() == "/api/user/initialization" && (c.Method() == "GET" || c.Method() == "POST") {
		return c.Next()
	}
	authStr := c.Get("Authorization")
	if authStr == "" {
		authStr = c.Cookies("X-Auth-Token", "")
	} else {
		authStr = strings.TrimPrefix(authStr, "Bearer ")
	}
	if authStr == "" {
		required, err := m.authenticateSrv.IsInitializationRequired(c)
		if err != nil {
			return err
		}
		if required {
			return c.Status(fiber.StatusPreconditionRequired).JSON(
				result.Error("系统尚未初始化管理员", map[string]bool{"initializationRequired": true}))
		}
		return errs.NewUnauthError("未登录")
	}
	username, err := m.jwtSrv.VerifyToken(authStr)
	if err != nil {
		return err
	}
	account, err := m.authenticateSrv.FindEnabledByUsername(c, username)
	if err != nil {
		return err
	}
	SetUserToFiberCtx(c, &AuthorityUser{ID: account.ID, Username: account.Username})
	return c.Next()
}

// Authorize 鉴权中间件
type Authorize struct {
	permissionSvc *AuthorizeService
}

func newAuthorize(permissionSrv *AuthorizeService) *Authorize {
	return &Authorize{permissionSvc: permissionSrv}
}

// permissionsKey 请求级权限集合在 Locals 中的键
//
// 一条路由可能挂多个权限中间件（MiddlewareAny），业务代码也可能再问一次权限；
// 权限在同一次请求内是快照语义，取一次就够，这里按请求缓存，避免重复 join 查询。
type permissionsKey struct{}

func (a *Authorize) HasAuthority(permission string) fiber.Handler {
	return a.HasAnyAuthority(permission)
}

func (a *Authorize) HasAnyAuthority(permissions ...string) fiber.Handler {
	return func(c fiber.Ctx) error {
		granted, err := a.requestPermissions(c)
		if err != nil {
			return err
		}
		for _, permission := range permissions {
			if _, ok := granted[permission]; ok {
				return c.Next()
			}
		}
		return errs.NewAuthError("没有权限")
	}
}

// requestPermissions 取当前请求的权限集合（首次调用时查库，之后复用）
func (a *Authorize) requestPermissions(c fiber.Ctx) (map[string]struct{}, error) {
	if cached, ok := c.Locals(permissionsKey{}).(map[string]struct{}); ok {
		return cached, nil
	}
	codes, err := a.permissionSvc.currentUserPermissionCodes(c)
	if err != nil {
		return nil, err
	}
	granted := make(map[string]struct{}, len(codes))
	for _, code := range codes {
		granted[code] = struct{}{}
	}
	c.Locals(permissionsKey{}, granted)
	return granted, nil
}
