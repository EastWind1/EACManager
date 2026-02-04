package result

import (
	"runtime/debug"

	"github.com/gofiber/fiber/v2/log"
	"github.com/spf13/viper"
)

// ActionsResult 批量操作结果
type ActionsResult[P any, R any] struct {
	// Results 操作结果
	Results []Row[P, R] `json:"results"`
	// SuccessCount 成功数量
	SuccessCount int `json:"successCount"`
	// FailCount 失败数量
	FailCount int `json:"failCount"`
}

// NewActionsResult 创建批量操作结果
func NewActionsResult[P any, R any](results *[]Row[P, R]) *ActionsResult[P, R] {
	res := ActionsResult[P, R]{
		Results: *results,
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

// Row 批量操作结果行
type Row[P any, R any] struct {
	// Param 操作参数
	Param P `json:"param"`
	// Result 操作结果
	Result R `json:"result"`
	// Success 是否成功
	Success bool `json:"success"`
	// Message 错误消息
	Message string `json:"message"`
}

// ExecuteActions 执行批量操作
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
			if viper.Get("log.level") == "debug" {
				debug.PrintStack()
			}
		}
		results = append(results, result)
	}
	return NewActionsResult(&results)
}
