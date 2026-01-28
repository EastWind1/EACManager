package controller

import (
	commonModel "backend-go/internal/common/result"
	"backend-go/internal/servicebill/model"
	"backend-go/internal/servicebill/service"
	"fmt"
	"net/http"

	"github.com/gin-gonic/gin"
)

type ServiceBillController struct {
	billService *service.ServiceBillService
}

func NewServiceBillController(billService *service.ServiceBillService) *ServiceBillController {
	return &ServiceBillController{
		billService: billService,
	}
}

func (c *ServiceBillController) GetByID(ctx *gin.Context) {
	idStr := ctx.Param("id")
	id, err := parseID(idStr)
	if err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any]("无效的 id"))
		return
	}

	result, err := c.billService.FindByID(id)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ServiceBillController) Create(ctx *gin.Context) {
	var dto model.ServiceBillDTO
	if err := ctx.ShouldBindJSON(&dto); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.billService.Create(&dto)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ServiceBillController) Update(ctx *gin.Context) {
	var dto model.ServiceBillDTO
	if err := ctx.ShouldBindJSON(&dto); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.billService.Update(&dto)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ServiceBillController) Delete(ctx *gin.Context) {
	var ids []int
	if err := ctx.ShouldBindJSON(&ids); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.billService.Delete(ids)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ServiceBillController) Process(ctx *gin.Context) {
	var ids []int
	if err := ctx.ShouldBindJSON(&ids); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.billService.Process(ids)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ServiceBillController) Processed(ctx *gin.Context) {
	var request struct {
		IDs           []int   `json:"ids" binding:"required"`
		ProcessedDate *string `json:"processedDate"`
	}
	if err := ctx.ShouldBindJSON(&request); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.billService.Processed(request.IDs, request.ProcessedDate)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ServiceBillController) Finish(ctx *gin.Context) {
	var request struct {
		IDs          []int   `json:"ids" binding:"required"`
		FinishedDate *string `json:"finishedDate"`
	}
	if err := ctx.ShouldBindJSON(&request); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.billService.Finish(request.IDs, request.FinishedDate)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func parseID(idStr string) (int, error) {
	id := 0
	_, err := fmt.Sscanf(idStr, "%d", &id)
	return id, err
}
