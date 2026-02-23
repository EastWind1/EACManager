package controller

import (
	"backend-go/internal/module/company/model"
	"backend-go/internal/module/company/service"
	"backend-go/internal/pkg/result"
	"strconv"

	"github.com/gofiber/fiber/v3"
)

type CompanyController struct {
	companyService *service.CompanyService
}

func NewCompanyController(companyService *service.CompanyService) *CompanyController {
	return &CompanyController{
		companyService: companyService,
	}
}

func (c *CompanyController) GetAll(ctx fiber.Ctx) error {
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

func (c *CompanyController) Create(ctx fiber.Ctx) error {
	var dto model.CompanyDTO
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

func (c *CompanyController) Update(ctx fiber.Ctx) error {
	var dto model.CompanyDTO
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

func (c *CompanyController) Disable(ctx fiber.Ctx) error {
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
