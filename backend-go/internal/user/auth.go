package user

import (
	"backend-go/pkg/auth"
	"backend-go/pkg/errs"
	"strings"

	"github.com/gofiber/fiber/v3"
)

// LoginPath 登录地址
const LoginPath = "/token"

// AuthMiddleware 认证中间件
func AuthMiddleware(jwtSrv *JWTService, userSrv *Service) fiber.Handler {
	return func(c fiber.Ctx) error {
		// 放行登录接口
		if c.Path() == "/api/user"+LoginPath && c.Method() == "POST" {
			return c.Next()
		}
		authStr := c.Get("Authorization")
		if authStr == "" {
			authStr = c.Cookies("X-Auth-Token", "")
		} else {
			authStr = strings.TrimPrefix(authStr, "Bearer ")
		}
		if authStr == "" {
			return errs.NewUnauthError("未登录")
		}
		token, err := jwtSrv.VerifyToken(authStr)
		if err != nil {
			return err
		}
		origin := c.Get("Origin")
		if origin == "" {
			origin = c.Get("Referer")
		}
		if !strings.HasPrefix(origin, token.Subject) {
			return errs.NewUnauthError("Token 不合法")
		}
		user, err := userSrv.FindByUsername(c, token.Username)
		if user.Disabled {
			return errs.NewUnauthError("Token 不合法")
		}

		auth.SetCurrentUser(c, user)
		return c.Next()
	}
}
