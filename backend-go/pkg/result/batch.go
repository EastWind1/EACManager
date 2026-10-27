package result

import (
	"github.com/gofiber/fiber/v3/log"
)

type ActionsResult[P any, R any] struct {
	Results []Row[P, R] `json:"results"`

	SuccessCount int `json:"successCount"`

	FailCount int `json:"failCount"`
}

func NewActionsResult[P any, R any](results []Row[P, R]) *ActionsResult[P, R] {
	res := ActionsResult[P, R]{
		Results: results,
	}
	for _, row := range res.Results {
		if row.Success {
			res.SuccessCount++
		} else {
			res.FailCount++
		}
	}
	return &res
}

type Row[P any, R any] struct {
	Param P `json:"param"`

	Result R `json:"result"`

	Success bool `json:"success"`

	Message string `json:"message"`
}

// ExecuteActions 逐项执行，单项失败不影响其他项，结果汇总为 ActionsResult。
func ExecuteActions[P any, R any](params []P, fn func(P) (R, error)) *ActionsResult[P, R] {
	results := make([]Row[P, R], 0, len(params))
	for _, p := range params {
		data, err := fn(p)
		result := Row[P, R]{
			Param:   p,
			Result:  data,
			Success: err == nil,
		}
		if err != nil {
			result.Message = err.Error()
			log.Errorf("操作参数: %v", p)
			log.Errorf("异常: %v", err)
		}
		results = append(results, result)
	}
	return NewActionsResult(results)
}
