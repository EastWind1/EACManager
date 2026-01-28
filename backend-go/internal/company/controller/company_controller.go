package controller

import (
	"backend-go/internal/common/result"
	"backend-go/internal/company/model"
	"backend-go/internal/company/service"
	"strconv"

	"github.com/gofiber/fiber/v2"
)

type CompanyController struct {
	companyService *service.CompanyService
}

func NewCompanyController(companyService *service.CompanyService) *CompanyController {
	return &CompanyController{
		companyService: companyService,
	}
}

func (c *CompanyController) GetAll(ctx *fiber.Ctx) error {
	var param result.QueryParam
	if err := ctx.QueryParser(&param); err != nil {
		return err
	}
	res, err := c.companyService.FindEnabled(ctx.Context(), &param)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *CompanyController) Create(ctx *fiber.Ctx) error {
	var dto model.CompanyDTO
	if err := ctx.BodyParser(&dto); err != nil {
		return err
	}
	res, err := c.companyService.Create(ctx.Context(), &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *CompanyController) Update(ctx *fiber.Ctx) error {
	var dto model.CompanyDTO
	if err := ctx.BodyParser(&dto); err != nil {
		return err
	}
	res, err := c.companyService.Update(ctx.Context(), &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *CompanyController) Disable(ctx *fiber.Ctx) error {
	param := ctx.Params("id")
	id, err := strconv.Atoi(param)
	if err != nil {
		return err
	}
	err = c.companyService.Disable(ctx.Context(), id)
	if err != nil {
		return err
	}

	return nil
}
