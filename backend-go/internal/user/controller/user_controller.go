package controller

import (
	"backend-go/config"
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/user/model"
	"backend-go/internal/user/service"

	"github.com/gofiber/fiber/v2"
)

// UserController 用户控制器
type UserController struct {
	cfg         *config.JWTConfig
	userService *service.UserService
}

// NewUserController 创建用户控制器实例
func NewUserController(cfg *config.JWTConfig, userService *service.UserService) *UserController {
	return &UserController{
		userService: userService,
		cfg:         cfg,
	}
}

// Login 用户登录
func (c *UserController) Login(ctx *fiber.Ctx) error {
	var param model.LoginParam
	err := ctx.BodyParser(&param)
	if err != nil {
		return err
	}
	origin := ctx.Get("Origin")
	res, err := c.userService.Login(ctx.Context(), param.Username, param.Password, origin)
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
	result.SetResult(ctx, res)
	return nil
}

// GetAll 获取用户列表
func (c *UserController) GetAll(ctx *fiber.Ctx) error {
	var param result.QueryParam
	err := ctx.QueryParser(&param)
	if err != nil {
		return err
	}
	res, err := c.userService.GetAll(ctx.Context(), &param)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

// Create 创建用户
func (c *UserController) Create(ctx *fiber.Ctx) error {
	var dto model.UserDTO
	if err := ctx.BodyParser(&dto); err != nil {
		return err
	}

	res, err := c.userService.Create(ctx.Context(), &dto)
	if err != nil {
		return err
	}

	result.SetResult(ctx, res)
	return nil
}

// Update 更新用户
func (c *UserController) Update(ctx *fiber.Ctx) error {
	var dto model.UserDTO
	if err := ctx.BodyParser(&dto); err != nil {
		return err
	}
	res, err := c.userService.Update(ctx.Context(), &dto)
	if err != nil {
		return err
	}

	result.SetResult(ctx, res)
	return nil
}

// Disable 禁用用户
func (c *UserController) Disable(ctx *fiber.Ctx) error {
	username := ctx.Params("username")
	if username == "" {
		return errs.NewBizError("用户名为空")
	}

	if err := c.userService.Disable(ctx.Context(), username); err != nil {
		return err
	}

	return nil
}
