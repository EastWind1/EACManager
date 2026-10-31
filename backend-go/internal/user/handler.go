package user

import (
	"backend-go/config"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"time"

	"github.com/gofiber/fiber/v3"
)

type Controller struct {
	cfg         *config.JWTConfig
	userService *Service
}

func NewController(cfg *config.JWTConfig, userService *Service) *Controller {
	return &Controller{
		userService: userService,
		cfg:         cfg,
	}
}

func (c *Controller) InitializationStatus(ctx fiber.Ctx) error {
	required, err := c.userService.IsInitializationRequired(ctx)
	if err != nil {
		return err
	}
	result.SetResult(ctx, InitStatus{Required: required})
	return nil
}

func (c *Controller) Initialize(ctx fiber.Ctx) error {
	var dto InitDTO
	if err := ctx.Bind().Body(&dto); err != nil {
		return err
	}
	user, err := c.userService.Initialize(ctx, &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, user)
	return nil
}

func (c *Controller) Login(ctx fiber.Ctx) error {
	var param LoginParam
	err := ctx.Bind().Body(&param)
	if err != nil {
		return err
	}
	res, err := c.userService.Login(ctx, param.Username, param.Password)
	if err != nil {
		return err
	}
	cookie := fiber.Cookie{
		Name:     "X-Auth-Token",
		Value:    res.Token,
		Path:     "/api",
		MaxAge:   c.cfg.Expire,
		Secure:   true,
		SameSite: "Strict",
		HTTPOnly: true,
	}
	ctx.Cookie(&cookie)
	result.SetResult(ctx, res.User)
	return nil
}

func (c *Controller) Logout(ctx fiber.Ctx) error {
	// 必须用与登录相同的 Path 覆盖，否则浏览器不会删除 /api 下的同名 Cookie
	ctx.Cookie(&fiber.Cookie{
		Name:     "X-Auth-Token",
		Value:    "",
		Path:     "/api",
		Expires:  time.Now().Add(-time.Hour),
		MaxAge:   -1,
		Secure:   true,
		SameSite: "Strict",
		HTTPOnly: true,
	})
	result.SetResult(ctx, nil)
	return nil
}

func (c *Controller) GetAll(ctx fiber.Ctx) error {
	var param result.QueryParam
	err := ctx.Bind().Query(&param)
	if err != nil {
		return err
	}
	res, err := c.userService.GetAll(ctx, &param)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Create(ctx fiber.Ctx) error {
	var dto DTO
	if err := ctx.Bind().Body(&dto); err != nil {
		return err
	}

	res, err := c.userService.Create(ctx, &dto)
	if err != nil {
		return err
	}

	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Update(ctx fiber.Ctx) error {
	var dto DTO
	if err := ctx.Bind().Body(&dto); err != nil {
		return err
	}
	res, err := c.userService.Update(ctx, &dto)
	if err != nil {
		return err
	}

	result.SetResult(ctx, res)
	return nil
}

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
