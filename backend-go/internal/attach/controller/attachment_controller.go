package controller

import (
	"backend-go/internal/attach/model"
	"backend-go/internal/attach/service"
	"backend-go/internal/common/result"
	"net/http"

	"github.com/gin-gonic/gin"
)

type AttachmentController struct {
	attachService *service.AttachmentService
}

func NewAttachmentController(attachService *service.AttachmentService) *AttachmentController {
	return &AttachmentController{
		attachService: attachService,
	}
}

func (c *AttachmentController) UploadTemp(ctx *gin.Context) {
	if err := ctx.Request.ParseMultipartForm(32 << 20); err != nil {
		ctx.JSON(http.StatusBadRequest, result.Error[any]("解析表单失败"))
		return
	}

	files := ctx.Request.MultipartForm.File["files"]
	if len(files) == 0 {
		ctx.JSON(http.StatusBadRequest, result.Error[any]("没有文件上传"))
		return
	}

	attachments, err := c.attachService.UploadTemp(files)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	result := make([]model.AttachmentDTO, len(attachments))
	for i, a := range attachments {
		result[i] = a.ToDTO()
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *AttachmentController) GetFile(ctx *gin.Context) {
	path := ctx.Param("path")
	if path == "" {
		ctx.JSON(http.StatusBadRequest, result.Error[any]("路径不能为空"))
		return
	}

	file, contentType, err := c.attachService.GetFile(path)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.Data(http.StatusOK, contentType, file)
}

func (c *AttachmentController) DeleteFile(ctx *gin.Context) {
	path := ctx.Param("path")
	if path == "" {
		ctx.JSON(http.StatusBadRequest, result.Error[any]("路径不能为空"))
		return
	}

	if err := c.attachService.DeleteFile(path); err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.Status(http.StatusNoContent)
}
