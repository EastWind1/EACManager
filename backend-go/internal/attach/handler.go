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

func (c *Controller) Upload(ctx fiber.Ctx) error {
	form, err := ctx.MultipartForm()
	if err != nil {
		return err
	}
	files := form.File["files"]
	if len(files) == 0 {
		return errs.NewBizError("没有文件上传")
	}
	// 命名空间由业务侧决定，附件模块只把它当作隔离前缀
	namespace := form.Value["namespace"]
	if len(namespace) == 0 {
		return errs.NewBizError("附件命名空间不能为空")
	}
	attachments, err := c.attachService.Upload(ctx, namespace[0], files)
	if err != nil {
		return err
	}

	result.SetResult(ctx, attachments)
	return nil
}

func (c *Controller) Download(ctx fiber.Ctx) error {
	var dto DTO
	if err := ctx.Bind().Query(&dto); err != nil {
		return err
	}
	name, path, err := c.attachService.GetResource(ctx, &dto)
	if err != nil {
		return err
	}
	return ctx.Download(path, name)
}
