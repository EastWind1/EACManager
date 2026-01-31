package controller

import (
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/reimburse/model"
	"backend-go/internal/reimburse/service"
	"strconv"

	"github.com/gofiber/fiber/v2"
)

type ReimburseController struct {
	reimburseService *service.ReimburseService
}

func NewReimburseController(reimburseService *service.ReimburseService) *ReimburseController {
	return &ReimburseController{
		reimburseService: reimburseService,
	}
}

func (c *ReimburseController) QueryByParam(ctx *fiber.Ctx) error {
	var param model.ReimburseQueryParam
	if err := ctx.QueryParser(&param); err != nil {
		return err
	}

	res, err := c.reimburseService.FindByParam(ctx.Context(), &param)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ReimburseController) GetByID(ctx *fiber.Ctx) error {
	idStr := ctx.Params("id")
	if idStr == "" {
		return errs.NewBizError("ID 为空")
	}
	id, err := strconv.Atoi(idStr)
	if err != nil {
		return err
	}
	res, err := c.reimburseService.FindByID(ctx.Context(), uint(id))
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ReimburseController) Create(ctx *fiber.Ctx) error {
	var dto model.ReimbursementDTO
	if err := ctx.BodyParser(&dto); err != nil {
		return err
	}

	res, err := c.reimburseService.Create(ctx.Context(), &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ReimburseController) Update(ctx *fiber.Ctx) error {
	var dto model.ReimbursementDTO
	if err := ctx.BodyParser(&dto); err != nil {
		return err
	}

	res, err := c.reimburseService.Update(ctx.Context(), &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ReimburseController) Delete(ctx *fiber.Ctx) error {
	var ids []uint
	if err := ctx.BodyParser(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.Delete(ctx.Context(), ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ReimburseController) Process(ctx *fiber.Ctx) error {
	var ids []uint
	if err := ctx.BodyParser(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.Process(ctx.Context(), ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ReimburseController) Finish(ctx *fiber.Ctx) error {
	var ids []uint
	if err := ctx.BodyParser(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.Finish(ctx.Context(), ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ReimburseController) Export(ctx *fiber.Ctx) error {
	var ids []uint
	if err := ctx.BodyParser(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.Export(ctx.Context(), ids)
	if err != nil {
		return err
	}
	return ctx.Download(res, "导出.zip")
}
