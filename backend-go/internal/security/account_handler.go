package security

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"strconv"

	"github.com/gofiber/fiber/v3"
)

type AccountController struct {
	srv *AuthenticateService
}

func newAccountController(srv *AuthenticateService) *AccountController {
	return &AccountController{srv: srv}
}

func (c *AccountController) Disable(ctx fiber.Ctx) error {
	id, err := strconv.ParseUint(ctx.Params("id"), 10, 64)
	if err != nil || id == 0 {
		return errs.NewBizError("用户 id 不合法")
	}
	if err := c.srv.Disable(ctx, uint(id)); err != nil {
		return err
	}
	result.SetResult(ctx, nil)
	return nil
}
