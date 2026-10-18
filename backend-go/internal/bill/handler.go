package bill

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"strconv"

	"github.com/gofiber/fiber/v3"
)

type Controller struct {
	srv *Service
}

func NewController(srv *Service) *Controller {
	return &Controller{
		srv: srv,
	}
}

func (c *Controller) QueryByParam(ctx fiber.Ctx) error {
	var param QueryParam
	if err := ctx.Bind().Body(&param); err != nil {
		return err
	}

	res, err := c.srv.FindByParam(ctx, &param)
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
	res, err := c.srv.FindByID(ctx, uint(id))
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

	res, err := c.srv.Create(ctx, &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) ImportByFile(ctx fiber.Ctx) error {
	form, err := ctx.MultipartForm()
	if err != nil {
		return err
	}
	files := form.File["file"]
	if len(files) == 0 {
		return errs.NewBizError("没有文件上传")
	}
	res, err := c.srv.GenerateByFile(ctx, files[0])
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

	res, err := c.srv.Update(ctx, &dto)
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

	res, err := c.srv.Delete(ctx, ids)
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

	res, err := c.srv.Process(ctx, ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Processed(ctx fiber.Ctx) error {
	var param ProcessedParam
	if err := ctx.Bind().Body(&param); err != nil {
		return err
	}

	res, err := c.srv.Processed(ctx, param.Ids, param.ProcessedDate)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Finish(ctx fiber.Ctx) error {
	var param FinishParam
	if err := ctx.Bind().Body(&param); err != nil {
		return err
	}

	res, err := c.srv.Finish(ctx, param.Ids, param.FinishedDate)
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

	res, err := c.srv.CancelProcess(ctx, ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) CancelProcessed(ctx fiber.Ctx) error {
	var ids []uint
	if err := ctx.Bind().Body(&ids); err != nil {
		return err
	}

	res, err := c.srv.CancelProcessed(ctx, ids)
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

	res, err := c.srv.CancelFinish(ctx, ids)
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

	res, err := c.srv.Export(ctx, ids)
	if err != nil {
		return err
	}
	return ctx.Download(res, "导出.zip")
}
