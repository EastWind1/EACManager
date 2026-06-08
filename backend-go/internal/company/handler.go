package company

import (
	"backend-go/pkg/result"
	"strconv"

	"github.com/gofiber/fiber/v3"
)

type Controller struct {
	companyService *Service
}

func NewController(companyService *Service) *Controller {
	return &Controller{
		companyService: companyService,
	}
}

func (c *Controller) GetAll(ctx fiber.Ctx) error {
	var param result.QueryParam
	if err := ctx.Bind().Query(&param); err != nil {
		return err
	}
	res, err := c.companyService.FindEnabled(ctx, &param)
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
	res, err := c.companyService.Create(ctx, &dto)
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
	res, err := c.companyService.Update(ctx, &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Disable(ctx fiber.Ctx) error {
	param := ctx.Params("id")
	id, err := strconv.Atoi(param)
	if err != nil {
		return err
	}
	err = c.companyService.Disable(ctx, uint(id))
	if err != nil {
		return err
	}

	return nil
}
