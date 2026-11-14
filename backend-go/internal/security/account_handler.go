package security

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"

	"github.com/gofiber/fiber/v3"
)

type AccountController struct {
	srv *AuthenticateService
}

func newAccountController(srv *AuthenticateService) *AccountController {
	return &AccountController{srv: srv}
}

func (c *AccountController) Disable(ctx fiber.Ctx) error {
	username := ctx.Params("username")
	if username == "" {
		return errs.NewBizError("用户名为空")
	}
	if err := c.srv.Disable(ctx, username); err != nil {
		return err
	}
	result.SetResult(ctx, nil)
	return nil
}
