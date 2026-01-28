package middleware

import (
	"backend-go/internal/common/auth"
	"backend-go/internal/common/errs"
	"backend-go/internal/user/service"
	"strings"

	"github.com/gofiber/fiber/v2"
)

// AuthMiddleware 认证中间件
func AuthMiddleware(jwtSrv *service.JWTService, userSrv *service.UserService) fiber.Handler {
	return func(c *fiber.Ctx) error {
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
		if err != nil || !user.IsEnabled || token.Subject != c.Get("Origin") {
			return errs.NewUnauthError("Token 不合法")
		}

		auth.SetCurrentUser(c, user)
		return c.Next()
	}
}
