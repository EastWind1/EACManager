package middleware

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"errors"

	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/log"
)

// ErrorHandler 错误处理
func ErrorHandler() fiber.ErrorHandler {
	return func(c fiber.Ctx, err error) error {
		if stackErr, ok := errors.AsType[errs.StackError](err); ok {
			log.Errorf("%v", stackErr.Error())
			log.Errorf("%v", string(stackErr.Stack()))

			var bizError *errs.BizError
			var authError *errs.AuthError
			var unauthError *errs.UnauthError
			var fileOpError *errs.FileOpError
			switch {
			case errors.As(stackErr, &bizError):
				return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any](bizError.Error()))
			case errors.As(stackErr, &authError):
				return c.Status(fiber.StatusForbidden).JSON(result.Error[any](authError.Error()))
			case errors.As(stackErr, &unauthError):
				return c.Status(fiber.StatusUnauthorized).JSON(result.Error[any](unauthError.Error()))
			case errors.As(stackErr, &fileOpError):
				return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any](fileOpError.ErrorWithoutPath()))
			}
		}

		// 其他未处理异常, 未处理异常由 recover 打印栈
		log.Errorf("%+v", err)
		return c.Status(500).JSON(result.Error[any]("内部异常"))
	}
}
