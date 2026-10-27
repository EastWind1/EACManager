package result

import (
	"github.com/gofiber/fiber/v3"
)

const responseDataKey = "response_data"

type Result[T any] struct {
	Message string `json:"message"`
	Data    T      `json:"data"`
}

func Ok[T any](data T) Result[T] {
	return Result[T]{Message: "OK", Data: data}
}

func Error[T any](message string, data ...T) Result[T] {
	res := Result[T]{Message: message}
	if len(data) > 0 {
		res.Data = data[0]
	}
	return res
}

// SetResult 设置响应体值，最终由中间件 ResultWrap 序列化为 JSON
func SetResult(c fiber.Ctx, data any) {
	c.Locals(responseDataKey, data)
}

func GetResult(c fiber.Ctx) any {
	return c.Value(responseDataKey)
}
