package user

import (
	"backend-go/config"
	"backend-go/pkg/result"
	"backend-go/pkg/validation"
	"strings"
	"time"

	"github.com/gofiber/fiber/v3"
)

type Controller struct {
	cfg         *config.JWTConfig
	userService *Service
}

func newController(cfg *config.JWTConfig, userService *Service) *Controller {
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
	var dto DTO
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
	if err = validation.Struct(&param); err != nil {
		return err
	}
	res, err := c.userService.Login(ctx, param.Username, param.Password)
	if err != nil {
		return err
	}
	c.setToken(ctx, res.Token)
	result.SetResult(ctx, res.User)
	return nil
}

func (c *Controller) Logout(ctx fiber.Ctx) error {
	// 先作废 token，再销毁 cookie；这样即使浏览器保留副本也无法继续访问。
	token := ctx.Cookies("X-Auth-Token", "")
	if token == "" {
		token = strings.TrimPrefix(ctx.Get("Authorization"), "Bearer ")
	}
	c.userService.Logout(token)
	c.clearCookie(ctx, "X-Auth-Token", "/api")
	result.SetResult(ctx, nil)
	return nil
}

func (c *Controller) setToken(ctx fiber.Ctx, token string) {
	ctx.Cookie(&fiber.Cookie{
		Name:     "X-Auth-Token",
		Value:    token,
		Path:     "/api",
		MaxAge:   c.cfg.Expire,
		Secure:   true,
		SameSite: "Strict",
		HTTPOnly: true,
	})
}

func (c *Controller) clearCookie(ctx fiber.Ctx, name, path string) {
	ctx.Cookie(&fiber.Cookie{
		Name:     name,
		Value:    "",
		Path:     path,
		Expires:  time.Now().Add(-time.Hour),
		MaxAge:   -1,
		Secure:   true,
		SameSite: "Strict",
		HTTPOnly: true,
	})
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
