package middleware

import (
	"backend-go/internal/common/result"

	"github.com/gofiber/fiber/v2"
)

// ResultWrap 响应体包装中间件
func ResultWrap() fiber.Handler {
	return func(c *fiber.Ctx) (err error) {
		err = c.Next()
		if err != nil {
			return
		}
		data := result.GetResult(c)
		if data != nil {
			return c.JSON(result.Ok(data))
		}
		return
	}
}
