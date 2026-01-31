package model

import (
	"backend-go/internal/common/result"
	"time"
)

// ReimburseQueryParam 报销单查询参数
type ReimburseQueryParam struct {
	Number             string           `json:"number"`
	Summary            string           `json:"summary"`
	States             []ReimburseState `json:"states"`
	ReimburseStartDate *time.Time       `json:"reimburseStartDate"`
	ReimburseEndDate   *time.Time       `json:"reimburseEndDate"`
	result.QueryParam
}
