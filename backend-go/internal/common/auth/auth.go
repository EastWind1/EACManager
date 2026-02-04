package auth

import (
	"backend-go/internal/common/errs"
	"context"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/log"
)

// currentUserKey 当前用户在上下文中的 Key
const currentUserKey = "current_user"

// AuthorityRole 角色
type AuthorityRole string

// 角色常量
const (
	// RoleAdmin 管理员
	RoleAdmin AuthorityRole = "ROLE_ADMIN"
	// RoleUser 用户
	RoleUser AuthorityRole = "ROLE_USER"
	// RoleGuest 游客
	RoleGuest AuthorityRole = "ROLE_GUEST"
	// RoleFinance 财务
	RoleFinance AuthorityRole = "ROLE_FINANCE"
)

func (a AuthorityRole) String() string {
	return string(a)
}

// User 鉴权用户
type User interface {
	GetID() uint
	GetRole() AuthorityRole
}

// SetCurrentUser 设置当前用户
func SetCurrentUser(c *fiber.Ctx, user User) {
	c.Context().SetUserValue(currentUserKey, user)
}

// GetCurrentUser 获取当前用户
func GetCurrentUser(c context.Context) User {
	data := c.Value(currentUserKey)
	if data == nil {
		return nil
	}
	user, ok := data.(User)
	if !ok {
		log.Error("转换 User 失败")
		return nil
	}
	return user
}

// HasRole 检查当前用户是否有指定的角色
func HasRole(ctx context.Context, roles ...AuthorityRole) (bool, error) {
	user := GetCurrentUser(ctx)
	if user == nil {
		return false, errs.NewUnauthError("未登录")
	}
	for _, role := range roles {
		if user.GetRole() == role {
			return true, nil
		}
	}
	return false, nil
}

// RoleMiddleware 角色鉴权中间件
func RoleMiddleware(roles ...AuthorityRole) fiber.Handler {
	return func(c *fiber.Ctx) error {
		res, err := HasRole(c.Context(), roles...)
		if err != nil {
			return err
		}
		if !res {
			return errs.NewAuthError("没有权限")
		}
		return c.Next()
	}
}
