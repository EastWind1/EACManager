package security

import (
	"backend-go/pkg/errs"
	"context"

	"github.com/gofiber/fiber/v3"
)

type CurUserKey struct{}

// BaseUser 鉴权用户；角色与权限由 security 模块按用户 ID 加载
type BaseUser interface {
	GetID() uint
}

func SetUserToFiberCtx(c fiber.Ctx, user BaseUser) {
	c.Locals(CurUserKey{}, user)
}

func SetUserToCtx(c context.Context, user BaseUser) context.Context {
	return context.WithValue(c, CurUserKey{}, user)
}

func GetCurrentUser(c context.Context) (BaseUser, error) {
	data := c.Value(CurUserKey{})
	if data == nil {
		return nil, errs.NewUnauthError("未登录")
	}
	currentUser, ok := data.(BaseUser)
	if !ok {
		return nil, errs.NewBizError("转换用户失败")
	}
	return currentUser, nil
}

// SystemUser 启动同步使用的系统身份
type SystemUser struct{}

func (SystemUser) GetID() uint { return 0 }
