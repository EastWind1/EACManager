package reimburse

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"strconv"

	"github.com/gofiber/fiber/v3"
)

type Controller struct {
	reimburseService *Service
}

func NewReimburseController(reimburseService *Service) *Controller {
	return &Controller{
		reimburseService: reimburseService,
	}
}

func (c *Controller) QueryByParam(ctx fiber.Ctx) error {
	var param QueryParam
	if err := ctx.Bind().Body(&param); err != nil {
		return err
	}

	res, err := c.reimburseService.FindByParam(ctx, &param)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) GetByID(ctx fiber.Ctx) error {
	idStr := ctx.Params("id")
	if idStr == "" {
		return errs.NewBizError("ID 为空")
	}
	id, err := strconv.Atoi(idStr)
	if err != nil {
		return err
	}
	res, err := c.reimburseService.FindByID(ctx, uint(id))
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

	res, err := c.reimburseService.Create(ctx, &dto)
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

	res, err := c.reimburseService.Update(ctx, &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Delete(ctx fiber.Ctx) error {
	var ids []uint
	if err := ctx.Bind().Body(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.Delete(ctx, ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Process(ctx fiber.Ctx) error {
	var ids []uint
	if err := ctx.Bind().Body(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.Process(ctx, ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Finish(ctx fiber.Ctx) error {
	var ids []uint
	if err := ctx.Bind().Body(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.Finish(ctx, ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) CancelProcess(ctx fiber.Ctx) error {
	var ids []uint
	if err := ctx.Bind().Body(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.CancelProcess(ctx, ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) CancelFinish(ctx fiber.Ctx) error {
	var ids []uint
	if err := ctx.Bind().Body(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.CancelFinish(ctx, ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Export(ctx fiber.Ctx) error {
	var ids []uint
	if err := ctx.Bind().Body(&ids); err != nil {
		return err
	}

	res, err := c.reimburseService.Export(ctx, ids)
	if err != nil {
		return err
	}
	return ctx.Download(res, "导出.zip")
}
