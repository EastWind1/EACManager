package reimburse

import (
	"backend-go/pkg/result"
	"time"
)

// QueryParam 报销单查询参数
type QueryParam struct {
	Number             string     `json:"number"`
	Summary            string     `json:"summary"`
	States             []State    `json:"states"`
	ReimburseStartDate *time.Time `json:"reimburseStartDate"`
	ReimburseEndDate   *time.Time `json:"reimburseEndDate"`
	result.QueryParam
}
