package bill

import (
	"backend-go/pkg/result"

	"github.com/gofiber/fiber/v3"
)

type StatisticController struct {
	statisticSrv *StatisticService
}

func NewStatisticController(statisticSrv *StatisticService) *StatisticController {
	return &StatisticController{
		statisticSrv: statisticSrv,
	}
}

func (c *StatisticController) CountBillsByState(ctx fiber.Ctx) error {
	res, err := c.statisticSrv.CountBillsByState(ctx)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *StatisticController) SumAmountByMonth(ctx fiber.Ctx) error {
	res, err := c.statisticSrv.SumAmountByMonth(ctx)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}
