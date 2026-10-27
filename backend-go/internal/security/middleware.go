package security

import (
	"backend-go/pkg/errs"

	"github.com/gofiber/fiber/v3"
)

// permissionsKey 请求级权限集合在 Locals 中的键
//
// 一条路由可能挂多个权限中间件（MiddlewareAny），业务代码也可能再问一次权限；
// 权限在同一次请求内是快照语义，取一次就够，这里按请求缓存，避免重复 join 查询。
type permissionsKey struct{}

func Middleware(srv *Service, permission string) fiber.Handler {
	return MiddlewareAny(srv, permission)
}

// MiddlewareAny 满足任意一个权限即可通过
func MiddlewareAny(srv *Service, permissions ...string) fiber.Handler {
	return func(c fiber.Ctx) error {
		granted, err := requestPermissions(c, srv)
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
func requestPermissions(c fiber.Ctx, srv *Service) (map[string]struct{}, error) {
	if cached, ok := c.Locals(permissionsKey{}).(map[string]struct{}); ok {
		return cached, nil
	}
	codes, err := srv.currentUserPermissionCodes(c)
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

// HasRequestPermission 复用中间件已取回的权限集合，供处理器内部判断
func HasRequestPermission(c fiber.Ctx, srv *Service, permission string) (bool, error) {
	granted, err := requestPermissions(c, srv)
	if err != nil {
		return false, err
	}
	_, ok := granted[permission]
	return ok, nil
}
