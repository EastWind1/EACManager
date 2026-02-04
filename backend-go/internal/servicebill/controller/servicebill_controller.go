package controller

import (
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/servicebill/model"
	"backend-go/internal/servicebill/service"
	"strconv"

	"github.com/gofiber/fiber/v2"
)

type ServiceBillController struct {
	bizSrv *service.BizService
}

func NewServiceBillController(bizSrv *service.BizService) *ServiceBillController {
	return &ServiceBillController{
		bizSrv: bizSrv,
	}
}

func (c *ServiceBillController) QueryByParam(ctx *fiber.Ctx) error {
	var param model.ServiceBillQueryParam
	if err := ctx.BodyParser(&param); err != nil {
		return err
	}

	res, err := c.bizSrv.FindByParam(ctx.Context(), &param)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) GetByID(ctx *fiber.Ctx) error {
	idStr := ctx.Params("id")
	if idStr == "" {
		return errs.NewBizError("ID 为空")
	}
	id, err := strconv.Atoi(idStr)
	if err != nil {
		return err
	}
	res, err := c.bizSrv.FindByID(ctx.Context(), uint(id))
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) Create(ctx *fiber.Ctx) error {
	var dto model.ServiceBillDTO
	if err := ctx.BodyParser(&dto); err != nil {
		return err
	}

	res, err := c.bizSrv.Create(ctx.Context(), &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) ImportByFile(ctx *fiber.Ctx) error {
	form, err := ctx.MultipartForm()
	if err != nil {
		return err
	}
	files := form.File["file"]
	if len(files) == 0 {
		return errs.NewBizError("没有文件上传")
	}
	res, err := c.bizSrv.GenerateByFile(ctx.Context(), files[0])
	if err != nil {
		return err
	}

	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) Update(ctx *fiber.Ctx) error {
	var dto model.ServiceBillDTO
	if err := ctx.BodyParser(&dto); err != nil {
		return err
	}

	res, err := c.bizSrv.Update(ctx.Context(), &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) Delete(ctx *fiber.Ctx) error {
	var ids []uint
	if err := ctx.BodyParser(&ids); err != nil {
		return err
	}

	res, err := c.bizSrv.Delete(ctx.Context(), ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) Process(ctx *fiber.Ctx) error {
	var ids []uint
	if err := ctx.BodyParser(&ids); err != nil {
		return err
	}

	res, err := c.bizSrv.Process(ctx.Context(), ids)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) Processed(ctx *fiber.Ctx) error {
	var param model.ProcessedParam
	if err := ctx.BodyParser(&param); err != nil {
		return err
	}

	res, err := c.bizSrv.Processed(ctx.Context(), param.Ids, param.ProcessedDate)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) Finish(ctx *fiber.Ctx) error {
	var param model.FinishParam
	if err := ctx.BodyParser(&param); err != nil {
		return err
	}

	res, err := c.bizSrv.Finish(ctx.Context(), param.Ids, param.FinishedDate)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *ServiceBillController) Export(ctx *fiber.Ctx) error {
	var ids []uint
	if err := ctx.BodyParser(&ids); err != nil {
		return err
	}

	res, err := c.bizSrv.Export(ctx.Context(), ids)
	if err != nil {
		return err
	}
	return ctx.Download(res, "导出.zip")
}
