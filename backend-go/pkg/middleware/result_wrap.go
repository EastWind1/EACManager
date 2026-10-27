package middleware

import (
	"backend-go/pkg/result"

	"github.com/gofiber/fiber/v3"
)

func ResultWrap() fiber.Handler {
	return func(c fiber.Ctx) (err error) {
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
