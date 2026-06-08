package middleware

import (
	errs2 "backend-go/pkg/errs"
	"backend-go/pkg/result"
	"errors"
	"runtime/debug"

	"github.com/gofiber/fiber/v3"
	"github.com/gofiber/fiber/v3/log"
)

// ErrorHandler 错误处理
func ErrorHandler() fiber.ErrorHandler {
	return func(c fiber.Ctx, err error) error {
		if stackErr, ok := errors.AsType[errs2.StackError](err); ok {
			log.Errorf("%v", stackErr.Error())
			log.Errorf("%v", string(stackErr.Stack()))

			var bizError *errs2.BizError
			var authError *errs2.AuthError
			var unauthError *errs2.UnauthError
			var fileOpError *errs2.FileOpError
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

		// 其他未处理异常
		if internalErr, ok := errors.AsType[*fiber.Error](err); ok {
			return c.Status(internalErr.Code).JSON(result.Error[any](internalErr.Message))
		}
		log.Errorf("%+v", err)
		debug.PrintStack()
		return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any]("服务器内部错误"))
	}
}
