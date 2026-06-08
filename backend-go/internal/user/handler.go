package user

import (
	"backend-go/config"
	"backend-go/pkg/errs"
	result2 "backend-go/pkg/result"

	"github.com/gofiber/fiber/v3"
)

// Controller 用户控制器
type Controller struct {
	cfg         *config.JWTConfig
	userService *Service
}

// NewController 创建用户控制器实例
func NewController(cfg *config.JWTConfig, userService *Service) *Controller {
	return &Controller{
		userService: userService,
		cfg:         cfg,
	}
}

// Login 用户登录
func (c *Controller) Login(ctx fiber.Ctx) error {
	var param LoginParam
	err := ctx.Bind().Body(&param)
	if err != nil {
		return err
	}
	origin := ctx.Get("Origin")
	res, err := c.userService.Login(ctx, param.Username, param.Password, origin)
	if err != nil {
		return err
	}
	cookie := fiber.Cookie{
		Name:     "X-Auth-Token",
		Value:    res.Token,
		Path:     "/",
		MaxAge:   c.cfg.Expire,
		Secure:   true,
		SameSite: "Strict",
		HTTPOnly: true,
	}
	ctx.Cookie(&cookie)
	result2.SetResult(ctx, res.User)
	return nil
}

// GetAll 获取用户列表
func (c *Controller) GetAll(ctx fiber.Ctx) error {
	var param result2.QueryParam
	err := ctx.Bind().Query(&param)
	if err != nil {
		return err
	}
	res, err := c.userService.GetAll(ctx, &param)
	if err != nil {
		return err
	}
	result2.SetResult(ctx, res)
	return nil
}

// Create 创建用户
func (c *Controller) Create(ctx fiber.Ctx) error {
	var dto DTO
	if err := ctx.Bind().Body(&dto); err != nil {
		return err
	}

	res, err := c.userService.Create(ctx, &dto)
	if err != nil {
		return err
	}

	result2.SetResult(ctx, res)
	return nil
}

// Update 更新用户
func (c *Controller) Update(ctx fiber.Ctx) error {
	var dto DTO
	if err := ctx.Bind().Body(&dto); err != nil {
		return err
	}
	res, err := c.userService.Update(ctx, &dto)
	if err != nil {
		return err
	}

	result2.SetResult(ctx, res)
	return nil
}

// Disable 禁用用户
func (c *Controller) Disable(ctx fiber.Ctx) error {
	username := ctx.Params("username")
	if username == "" {
		return errs.NewBizError("用户名为空")
	}

	if err := c.userService.Disable(ctx, username); err != nil {
		return err
	}

	return nil
}
