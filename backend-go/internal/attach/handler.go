package attach

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"

	"github.com/gofiber/fiber/v3"
)

type Controller struct {
	attachService *Service
}

func NewController(attachService *Service) *Controller {
	return &Controller{
		attachService: attachService,
	}
}

func (c *Controller) UploadTemp(ctx fiber.Ctx) error {
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

func (c *Controller) Download(ctx fiber.Ctx) error {
	var dto AttachmentDTO
	if err := ctx.Bind().Query(&dto); err != nil {
		return err
	}
	name, path, err := c.attachService.GetResource(ctx, &dto)
	if err != nil {
		return err
	}
	return ctx.Download(path, name)
}
