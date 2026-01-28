package controller

import (
	commonModel "backend-go/internal/common/result"
	"backend-go/internal/reimburse/model"
	"backend-go/internal/reimburse/service"
	"fmt"
	"net/http"

	"github.com/gin-gonic/gin"
)

type ReimburseController struct {
	reimburseService *service.ReimburseService
}

func NewReimburseController(reimburseService *service.ReimburseService) *ReimburseController {
	return &ReimburseController{
		reimburseService: reimburseService,
	}
}

func (c *ReimburseController) Query(ctx *gin.Context) {
	var param model.ReimburseQueryParam
	if err := ctx.ShouldBindQuery(&param); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, commonModel.Ok([]model.ReimbursementDTO{}))
}

func (c *ReimburseController) GetByID(ctx *gin.Context) {
	idStr := ctx.Param("id")
	id, err := parseID(idStr)
	if err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any]("无效的 id"))
		return
	}

	result, err := c.reimburseService.FindByID(id)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ReimburseController) Create(ctx *gin.Context) {
	var dto model.ReimbursementDTO
	if err := ctx.ShouldBindJSON(&dto); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.reimburseService.Create(&dto)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ReimburseController) Update(ctx *gin.Context) {
	var dto model.ReimbursementDTO
	if err := ctx.ShouldBindJSON(&dto); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.reimburseService.Update(&dto)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ReimburseController) Delete(ctx *gin.Context) {
	var ids []int
	if err := ctx.ShouldBindJSON(&ids); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.reimburseService.Delete(ids)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ReimburseController) Process(ctx *gin.Context) {
	var ids []int
	if err := ctx.ShouldBindJSON(&ids); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.reimburseService.Process(ids)
	if err != nil {
		ctx.JSON(http.StatusOK, result.Error[any](err.Error()))
		return
	}

	ctx.JSON(http.StatusOK, result.Ok(result))
}

func (c *ReimburseController) Finish(ctx *gin.Context) {
	var ids []int
	if err := ctx.ShouldBindJSON(&ids); err != nil {
		ctx.JSON(http.StatusBadRequest, commonModel.Error[any](err.Error()))
		return
	}

	result, err := c.reimburseService.Finish(ids)
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
