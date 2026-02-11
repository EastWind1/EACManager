package middleware

import (
	service2 "backend-go/internal/module/user/service"
	"backend-go/internal/pkg/auth"
	"backend-go/internal/pkg/errs"
	"strings"

	"github.com/gofiber/fiber/v2"
)

// LoginPath 登录地址
const LoginPath = "/token"

// AuthMiddleware 认证中间件
func AuthMiddleware(jwtSrv *service2.JWTService, userSrv *service2.UserService) fiber.Handler {
	return func(c *fiber.Ctx) error {
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
		user, err := userSrv.FindByUsername(c.Context(), token.Username)
		origin := c.Get("Origin")
		if origin == "" {
			origin = c.Get("Referer")
		}
		if err != nil || user.Disabled || !strings.HasPrefix(origin, token.Subject) {
			return errs.NewUnauthError("Token 不合法")
		}

		auth.SetCurrentUser(c, user)
		return c.Next()
	}
}
