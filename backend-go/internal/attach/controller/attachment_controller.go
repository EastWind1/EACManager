package controller

import (
	"backend-go/internal/attach/model"
	"backend-go/internal/attach/service"
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"

	"github.com/gofiber/fiber/v2"
)

type AttachmentController struct {
	attachService *service.AttachmentService
}

func NewAttachmentController(attachService *service.AttachmentService) *AttachmentController {
	return &AttachmentController{
		attachService: attachService,
	}
}

func (c *AttachmentController) UploadTemp(ctx *fiber.Ctx) error {
	form, err := ctx.MultipartForm()
	if err != nil {
		return err
	}
	files := form.File["files"]
	if len(files) == 0 {
		return errs.NewBizError("没有文件上传")
	}
	temps, err := c.attachService.UploadTemps(&files)
	if err != nil {
		return err
	}

	result.SetResult(ctx, temps)
	return nil
}

func (c *AttachmentController) Download(ctx *fiber.Ctx) error {
	var dto model.AttachmentDTO
	if err := ctx.QueryParser(&dto); err != nil {
		return err
	}
	name, path, err := c.attachService.GetResource(ctx.Context(), &dto)
	if err != nil {
		return err
	}
	return ctx.Download(path, name)
}
