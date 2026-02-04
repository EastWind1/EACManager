package middleware

import (
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"errors"
	"runtime/debug"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/log"
)

// ErrorHandler 错误处理
func ErrorHandler() fiber.ErrorHandler {
	return func(c *fiber.Ctx, err error) error {
		var stackErr errs.StackError
		if errors.As(err, &stackErr) {
			log.Errorf("%v", stackErr.Error())
			log.Errorf("%v", string(stackErr.Stack()))

			// 业务异常
			var bizErr *errs.BizError
			if errors.As(err, &bizErr) {
				return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any](bizErr.Error()))
			}
			// 鉴权异常
			var authErr *errs.AuthError
			if errors.As(err, &authErr) {
				return c.Status(fiber.StatusForbidden).JSON(result.Error[any](authErr.Error()))
			}
			// 未认证异常
			var unauthErr *errs.UnauthError
			if errors.As(err, &unauthErr) {
				return c.Status(fiber.StatusUnauthorized).JSON(result.Error[any](unauthErr.Error()))
			}
			// 文件操作异常
			var fileOpErr *errs.FileOpError
			if errors.As(err, &fileOpErr) {
				return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any](fileOpErr.ErrorWithoutPath()))
			}
		}

		// 其他未处理异常
		var internalErr *fiber.Error
		if errors.As(err, &internalErr) {
			return c.Status(internalErr.Code).JSON(result.Error[any](internalErr.Message))
		}
		debug.PrintStack()
		return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any]("服务器内部错误"))
	}
}
