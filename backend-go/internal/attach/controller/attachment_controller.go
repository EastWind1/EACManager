package controller

import (
	"backend-go/internal/attach/model"
	"backend-go/internal/attach/service"
	"backend-go/internal/common/result"
	"net/http"
	"strconv"

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

	attachments, err := c.attachService.UploadTemps(files)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	result := make([]model.AttachmentDTO, len(attachments))
	for i, a := range attachments {
		result[i] = model.AttachmentDTO{
			ID:           a.ID,
			Name:         a.Name,
			Type:         a.Type,
			RelativePath: a.RelativePath,
			Temp:         true,
		}
	}

	ctx.JSON(http.StatusOK, result)
}

func (c *AttachmentController) DownloadFile(ctx *gin.Context) {
	dto := &model.AttachmentDTO{}
	if err := ctx.ShouldBind(dto); err != nil {
		ctx.JSON(http.StatusBadRequest, result.Error[any]("参数不能为空"))
		return
	}

	file, contentType, err := c.attachService.GetResource(dto)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.Header("Content-Disposition", "attachment; filename="+dto.Name)
	ctx.Data(http.StatusOK, contentType, file)
}

func (c *AttachmentController) GetAttachmentByBill(ctx *gin.Context) {
	billIDStr := ctx.Query("billId")
	billTypeStr := ctx.Query("billType")

	if billIDStr == "" {
		ctx.JSON(http.StatusBadRequest, result.Error[any]("单据ID不能为空"))
		return
	}

	billID, err := strconv.Atoi(billIDStr)
	if err != nil {
		ctx.JSON(http.StatusBadRequest, result.Error[any]("单据ID格式错误"))
		return
	}

	var billType model.BillType
	switch billTypeStr {
	case "SERVICE_BILL":
		billType = model.BillTypeServiceBill
	case "REIMBURSEMENT":
		billType = model.BillTypeReimbursement
	default:
		ctx.JSON(http.StatusBadRequest, result.Error[any]("无效的单据类型"))
		return
	}

	attachments, err := c.attachService.GetByBill(billID, billType)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	result := model.ToDTOs(attachments)
	ctx.JSON(http.StatusOK, result)
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

func (c *AttachmentController) UpdateRelativeAttach(ctx *gin.Context) {
	var billDTO struct {
		BillID         int                   `json:"billId" binding:"required"`
		BillNumber     string                `json:"billNumber" binding:"required"`
		BillType       string                `json:"billType" binding:"required"`
		AttachmentDTOs []model.AttachmentDTO `json:"attachmentDTOs"`
	}

	if err := ctx.ShouldBindJSON(&billDTO); err != nil {
		ctx.JSON(http.StatusBadRequest, result.Error[any]("参数解析失败"))
		return
	}

	var billType model.BillType
	switch billDTO.BillType {
	case "SERVICE_BILL":
		billType = model.BillTypeServiceBill
	case "REIMBURSEMENT":
		billType = model.BillTypeReimbursement
	default:
		ctx.JSON(http.StatusBadRequest, result.Error[any]("无效的单据类型"))
		return
	}

	attachments, err := c.attachService.UpdateRelativeAttach(billDTO.BillID, billDTO.BillNumber, billType, billDTO.AttachmentDTOs)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	result := model.ToDTOs(attachments)
	ctx.JSON(http.StatusOK, result)
}
