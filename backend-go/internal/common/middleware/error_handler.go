package middleware

import (
	ierror "backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"errors"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/log"
)

// ErrorHandler 错误处理
func ErrorHandler() fiber.ErrorHandler {
	return func(c *fiber.Ctx, err error) error {
		log.Errorf("ErrorHandler: %+v", err)
		// 业务异常
		var bizErr *ierror.BizError
		if errors.As(err, &bizErr) {
			return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any](bizErr.Message))
		}
		// 鉴权异常
		var authErr *ierror.AuthError
		if errors.As(err, &authErr) {
			return c.Status(fiber.StatusForbidden).JSON(result.Error[any](authErr.Message))
		}
		// 未认证异常
		var unauthErr *ierror.UnauthError
		if errors.As(err, &unauthErr) {
			return c.Status(fiber.StatusUnauthorized).JSON(result.Error[any](unauthErr.Message))
		}
		// 其他未处理异常
		var internalErr *fiber.Error
		if errors.As(err, &internalErr) {
			return c.Status(internalErr.Code).JSON(result.Error[any](internalErr.Message))
		}
		return c.Status(fiber.StatusInternalServerError).JSON(result.Error[any]("服务器内部错误"))
	}
}
