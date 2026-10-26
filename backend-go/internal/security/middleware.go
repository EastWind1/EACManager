package security

import (
	"backend-go/pkg/errs"

	"github.com/gofiber/fiber/v3"
)

// Middleware 权限中间件
func Middleware(srv *Service, permission string) fiber.Handler {
	return MiddlewareAny(srv, permission)
}

// MiddlewareAny 满足任意一个权限即可通过
func MiddlewareAny(srv *Service, permissions ...string) fiber.Handler {
	return func(c fiber.Ctx) error {
		for _, permission := range permissions {
			ok, err := srv.HasPermission(c, permission)
			if err != nil {
				return err
			}
			if ok {
				return c.Next()
			}
		}
		return errs.NewAuthError("没有权限")
	}
}
