package middleware

import (
	"backend-go/internal/pkg/errs"
	"backend-go/internal/pkg/result"
	"errors"
	"runtime/debug"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/log"
)

// ErrorHandler 错误处理
func ErrorHandler() fiber.ErrorHandler {
	return func(c *fiber.Ctx, err error) error {
		if stackErr, ok := errors.AsType[errs.StackError](err); ok {
			log.Errorf("%v", stackErr.Error())
			log.Errorf("%v", string(stackErr.Stack()))

			// 业务异常
			if bizErr, ok := errors.AsType[*errs.BizError](err); ok {
				return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any](bizErr.Error()))
			}
			// 鉴权异常
			if authErr, ok := errors.AsType[*errs.AuthError](err); ok {
				return c.Status(fiber.StatusForbidden).JSON(result.Error[any](authErr.Error()))
			}
			// 未认证异常
			if unauthErr, ok := errors.AsType[*errs.UnauthError](err); ok {
				return c.Status(fiber.StatusUnauthorized).JSON(result.Error[any](unauthErr.Error()))
			}
			// 文件操作异常
			if fileOpErr, ok := errors.AsType[*errs.FileOpError](err); ok {
				return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any](fileOpErr.ErrorWithoutPath()))
			}
		}

		// 其他未处理异常
		if internalErr, ok := errors.AsType[*fiber.Error](err); ok {
			return c.Status(internalErr.Code).JSON(result.Error[any](internalErr.Message))
		}
		log.Errorf("%+v", err)
		debug.PrintStack()
		return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any]("服务器内部错误"))
	}
}
