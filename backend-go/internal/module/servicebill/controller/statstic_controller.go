package controller

import (
	"backend-go/internal/module/servicebill/service"
	"backend-go/internal/pkg/result"

	"github.com/gofiber/fiber/v2"
)

type StatisticController struct {
	statisticSrv *service.StatisticService
}

func NewStatisticController(statisticSrv *service.StatisticService) *StatisticController {
	return &StatisticController{
		statisticSrv: statisticSrv,
	}
}

func (c *StatisticController) CountBillsByState(ctx *fiber.Ctx) error {
	res, err := c.statisticSrv.CountBillsByState(ctx.Context())
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *StatisticController) SumAmountByMonth(ctx *fiber.Ctx) error {
	res, err := c.statisticSrv.SumAmountByMonth(ctx.Context())
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}
