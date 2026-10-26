package auth

import (
	"backend-go/pkg/errs"
	"context"

	"github.com/gofiber/fiber/v3"
)

// CurUserKey 当前用户在上下文中的 Key
type CurUserKey struct{}

// User 鉴权用户；角色与权限由 security 模块按用户 ID 加载
type User interface {
	GetID() uint
}

// SetCurrentUser 设置当前用户
func SetCurrentUser(c fiber.Ctx, user User) {
	c.Locals(CurUserKey{}, user)
}

// GetCurrentUser 获取当前用户
func GetCurrentUser(c context.Context) (User, error) {
	data := c.Value(CurUserKey{})
	if data == nil {
		return nil, errs.NewUnauthError("未登录")
	}
	user, ok := data.(User)
	if !ok {
		return nil, errs.NewBizError("转换用户失败")
	}
	return user, nil
}
