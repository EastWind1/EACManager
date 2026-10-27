package user

import (
	"backend-go/pkg/auth"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"strings"

	"github.com/gofiber/fiber/v3"
)

const LoginPath = "/token"

func AuthMiddleware(jwtSrv *JWTService, userSrv *Service) fiber.Handler {
	return func(c fiber.Ctx) error {
		// 放行登录和初始化接口
		if c.Path() == "/api/user"+LoginPath && c.Method() == "POST" {
			return c.Next()
		}
		if c.Path() == "/api/user/initialization" &&
			(c.Method() == "GET" || c.Method() == "POST") {
			return c.Next()
		}
		authStr := c.Get("Authorization")
		if authStr == "" {
			authStr = c.Cookies("X-Auth-Token", "")
		} else {
			authStr = strings.TrimPrefix(authStr, "Bearer ")
		}
		if authStr == "" {
			required, err := userSrv.IsInitializationRequired(c)
			if err != nil {
				return err
			}
			if required {
				return c.Status(fiber.StatusPreconditionRequired).JSON(
					result.Error("系统尚未初始化管理员", map[string]bool{"initializationRequired": true}))
			}
			return errs.NewUnauthError("未登录")
		}
		sub, err := jwtSrv.VerifyToken(authStr)
		if err != nil {
			return err
		}
		user, err := userSrv.FindByUsername(c, sub)
		if err != nil {
			return err
		}
		if user.Disabled {
			return errs.NewUnauthError("Token 不合法")
		}

		auth.SetCurrentUser(c, user)
		return c.Next()
	}
}
