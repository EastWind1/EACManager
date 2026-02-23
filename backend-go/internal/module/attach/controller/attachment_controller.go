package controller

import (
	"backend-go/internal/module/attach/model"
	"backend-go/internal/module/attach/service"
	"backend-go/internal/pkg/errs"
	"backend-go/internal/pkg/result"

	"github.com/gofiber/fiber/v3"
)

type AttachmentController struct {
	attachService *service.AttachmentService
}

func NewAttachmentController(attachService *service.AttachmentService) *AttachmentController {
	return &AttachmentController{
		attachService: attachService,
	}
}

func (c *AttachmentController) UploadTemp(ctx fiber.Ctx) error {
	form, err := ctx.MultipartForm()
	if err != nil {
		return err
	}
	files := form.File["files"]
	if len(files) == 0 {
		return errs.NewBizError("没有文件上传")
	}
	temps, err := c.attachService.UploadTemps(files)
	if err != nil {
		return err
	}

	result.SetResult(ctx, temps)
	return nil
}

func (c *AttachmentController) Download(ctx fiber.Ctx) error {
	var dto model.AttachmentDTO
	if err := ctx.Bind().Query(&dto); err != nil {
		return err
	}
	name, path, err := c.attachService.GetResource(ctx, &dto)
	if err != nil {
		return err
	}
	return ctx.Download(path, name)
}
