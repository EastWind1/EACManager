package security

import (
	"backend-go/pkg/errs"
	"context"

	"github.com/gofiber/fiber/v3"
)

type CurUserKey struct{}

func SetUserToFiberCtx(c fiber.Ctx, user *AuthorityUser) {
	c.Locals(CurUserKey{}, user)
}

func SetUserToCtx(c context.Context, user *AuthorityUser) context.Context {
	return context.WithValue(c, CurUserKey{}, user)
}

func GetCurrentUser(c context.Context) (*AuthorityUser, error) {
	data := c.Value(CurUserKey{})
	if data == nil {
		return nil, errs.NewUnauthError("未登录")
	}
	currentUser, ok := data.(*AuthorityUser)
	if !ok {
		return nil, errs.NewBizError("转换用户失败")
	}
	return currentUser, nil
}
