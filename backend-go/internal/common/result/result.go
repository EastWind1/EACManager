package result

import (
	"github.com/gofiber/fiber/v2"
)

// responseDataKey 逻辑结果在上下文中的 Key
const responseDataKey = "response_data"

// Result 通用返回结果
type Result[T any] struct {
	Message string `json:"message"`
	Data    T      `json:"data"`
}

// Ok 返回成功结果
func Ok[T any](data T) Result[T] {
	return Result[T]{Message: "OK", Data: data}
}

// Error 返回错误结果
func Error[T any](message string, data ...T) Result[T] {
	res := Result[T]{Message: message}
	if len(data) > 0 {
		res.Data = data[0]
	}
	return res
}

// SetResult 设置响应体值，最终由中间件 ResultWrap 序列化为 JSON
func SetResult(c *fiber.Ctx, data any) {
	c.Context().SetUserValue(responseDataKey, data)
}

// GetResult 获取响应体值
func GetResult(c *fiber.Ctx) any {
	return c.Context().Value(responseDataKey)
}
